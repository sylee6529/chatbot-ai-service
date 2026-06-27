package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiContextMessage
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiMessageRole
import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.common.NotFoundException
import com.example.aichatbot.common.PageResponse
import com.example.aichatbot.knowledge.ChatDocumentSource
import com.example.aichatbot.knowledge.ChatDocumentSourceRepository
import com.example.aichatbot.knowledge.ChatSourceResponse
import com.example.aichatbot.knowledge.DocumentChunkRepository
import com.example.aichatbot.knowledge.KnowledgeContextService
import com.example.aichatbot.knowledge.RetrievedKnowledgeSource
import com.example.aichatbot.report.ActivityLogService
import com.example.aichatbot.report.ActivityType
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

@Service
class ChatService(
    private val userRepository: UserRepository,
    private val threadRepository: ChatThreadRepository,
    private val chatRepository: ChatRepository,
    private val documentChunkRepository: DocumentChunkRepository,
    private val chatDocumentSourceRepository: ChatDocumentSourceRepository,
    private val aiClient: AiClient,
    private val knowledgeContextService: KnowledgeContextService,
    private val activityLogService: ActivityLogService,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
    @Value("\${chat.thread.window-minutes:30}") private val threadWindowMinutes: Long,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun createChat(userId: Long, request: CreateChatRequest): CreateChatResponse {
        val question = request.question?.trim().orEmpty()
        if (question.isBlank()) {
            throw BadRequestException("question must not be blank")
        }

        val model = normalizeModel(request.model)
        val knowledgeContext = knowledgeContextService.retrieve(
            question = question,
            enabled = request.useKnowledgeBase ?: true,
        )
        val context = knowledgeContext.messages + loadContextSnapshot(userId)
        val aiResponse = aiClient.generate(
            AiGenerateRequest(
                question = question,
                context = context,
                model = model,
            ),
        )

        return saveChat(userId, question, aiResponse.answer, aiResponse.model, knowledgeContext.sources)
    }

    fun createStreamingChat(userId: Long, request: CreateChatRequest): SseEmitter {
        val question = request.question?.trim().orEmpty()
        if (question.isBlank()) {
            throw BadRequestException("question must not be blank")
        }

        val model = normalizeModel(request.model)
        val emitter = SseEmitter(60_000L)
        CompletableFuture.runAsync {
            try {
                val knowledgeContext = knowledgeContextService.retrieve(
                    question = question,
                    enabled = request.useKnowledgeBase ?: true,
                )
                val context = loadContextSnapshot(userId)
                val aiResponse = aiClient.generate(
                    AiGenerateRequest(
                        question = question,
                        context = knowledgeContext.messages + context,
                        model = model,
                    ),
                )
                aiResponse.answer.chunked(32).ifEmpty { listOf("") }.forEach {
                    emitter.send(SseEmitter.event().name("chunk").data(mapOf("delta" to it)))
                }
                val saved = saveChat(userId, question, aiResponse.answer, aiResponse.model, knowledgeContext.sources)
                emitter.send(
                    SseEmitter.event()
                        .name("done")
                        .data(mapOf("chatId" to saved.chatId, "threadId" to saved.threadId, "model" to saved.model)),
                )
                emitter.complete()
            } catch (exception: Exception) {
                runCatching {
                    emitter.send(
                        SseEmitter.event()
                            .name("error")
                            .data(mapOf("message" to (exception.message ?: "Streaming failed"))),
                    )
                }
                emitter.completeWithError(exception)
            }
        }
        return emitter
    }

    fun getThreadChats(
        currentUserId: Long,
        currentUserRole: Role,
        requestedUserId: Long?,
        page: Int,
        size: Int,
        sort: String?,
    ): PageResponse<ThreadChatsResponse> {
        if (page < 0) {
            throw BadRequestException("page must be greater than or equal to 0")
        }
        if (size !in 1..100) {
            throw BadRequestException("size must be between 1 and 100")
        }
        val sortSpec = parseSort(sort)
        val scopedUserId = when (currentUserRole) {
            Role.MEMBER -> {
                if (requestedUserId != null && requestedUserId != currentUserId) {
                    throw ForbiddenException("userId is only available for admins")
                }
                currentUserId
            }
            Role.ADMIN -> requestedUserId
        }

        val threadsPage = threadRepository.findActiveThreads(
            scopedUserId,
            PageRequest.of(page, size, Sort.by(sortSpec.direction, sortSpec.property)),
        )
        val threadIds = threadsPage.content.mapNotNull { it.id }
        val chats = if (threadIds.isEmpty()) {
            emptyList()
        } else {
            chatRepository.findByThreadIdInAndDeletedAtIsNullOrderByCreatedAtAsc(threadIds)
        }
        val chatsByThreadId = if (chats.isEmpty()) {
            emptyMap()
        } else {
            chats.groupBy { requireNotNull(it.thread.id) }
        }
        val sourcesByChatId = if (chats.isEmpty()) {
            emptyMap()
        } else {
            chatDocumentSourceRepository.findByChatIdInOrderByIdAsc(chats.mapNotNull { it.id })
                .groupBy { requireNotNull(it.chat.id) }
                .mapValues { (_, sources) ->
                    sources.map {
                        ChatSourceResponse(
                            documentTitle = it.documentTitle,
                            chunkIndex = it.chunkIndex,
                        )
                    }
                }
        }
        val content = threadsPage.content.map { thread ->
            ThreadChatsResponse(
                threadId = requireNotNull(thread.id),
                userId = requireNotNull(thread.user.id),
                createdAt = thread.createdAt,
                updatedAt = thread.updatedAt,
                chats = chatsByThreadId[requireNotNull(thread.id)].orEmpty().map {
                    it.toItemResponse(sourcesByChatId[requireNotNull(it.id)].orEmpty())
                },
            )
        }

        return PageResponse.of(threadsPage, content, sortSpec.raw)
    }

    private fun loadContextSnapshot(userId: Long): List<AiContextMessage> =
        transactionTemplate.execute {
            userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow { NotFoundException("User not found") }
            val now = Instant.now(clock)
            val latestChat = chatRepository.findTopByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .orElse(null)

            if (latestChat == null || Duration.between(latestChat.createdAt, now) > Duration.ofMinutes(threadWindowMinutes)) {
                emptyList()
            } else {
                chatRepository.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(requireNotNull(latestChat.thread.id))
                    .toAiContext()
            }
        } ?: emptyList()

    private fun saveChat(
        userId: Long,
        question: String,
        answer: String,
        model: String,
        knowledgeSources: List<RetrievedKnowledgeSource>,
    ): CreateChatResponse =
        requireNotNull(
            transactionTemplate.execute {
                val user = userRepository.findActiveByIdForUpdate(userId)
                    .orElseThrow { NotFoundException("User not found") }
                val now = Instant.now(clock)
                val thread = resolveThread(user, question, now)

                thread.markUpdated(now)
                val chat = chatRepository.save(
                    Chat(
                        thread = thread,
                        user = user,
                        question = question,
                        answer = answer,
                        model = model,
                        createdAt = now,
                    ),
                )
                knowledgeSources.forEach { source ->
                    chatDocumentSourceRepository.save(
                        ChatDocumentSource(
                            chat = chat,
                            documentChunk = documentChunkRepository.getReferenceById(source.chunkId),
                            documentTitle = source.documentTitle,
                            chunkIndex = source.chunkIndex,
                            score = source.score,
                            createdAt = now,
                        ),
                    )
                }
                activityLogService.record(user, ActivityType.CHAT_CREATED)

                CreateChatResponse(
                    chatId = requireNotNull(chat.id),
                    threadId = requireNotNull(thread.id),
                    question = chat.question,
                    answer = chat.answer,
                    model = requireNotNull(chat.model),
                    createdAt = chat.createdAt,
                    sources = knowledgeSources.map {
                        ChatSourceResponse(
                            documentTitle = it.documentTitle,
                            chunkIndex = it.chunkIndex,
                        )
                    },
                )
            },
        )

    private fun resolveThread(user: User, question: String, now: Instant): ChatThread {
        val latestChat = chatRepository.findTopByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(requireNotNull(user.id))
            .orElse(null)

        if (latestChat != null && Duration.between(latestChat.createdAt, now) <= Duration.ofMinutes(threadWindowMinutes)) {
            return latestChat.thread
        }

        return threadRepository.save(
            ChatThread(
                user = user,
                title = question.take(200),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun normalizeModel(model: String?): String? {
        if (model == null) {
            return null
        }
        val trimmed = model.trim()
        if (trimmed.isBlank()) {
            throw BadRequestException("model must not be blank")
        }
        if (!MODEL_PATTERN.matches(trimmed)) {
            throw BadRequestException("model contains unsupported characters")
        }
        return trimmed
    }

    private fun List<Chat>.toAiContext(): List<AiContextMessage> =
        flatMap {
            listOf(
                AiContextMessage(role = AiMessageRole.USER, content = it.question),
                AiContextMessage(role = AiMessageRole.ASSISTANT, content = it.answer),
            )
        }

    companion object {
        private val MODEL_PATTERN = Regex("^[A-Za-z0-9._:/+-]{1,100}$")
    }
}

private data class ThreadSortSpec(
    val property: String,
    val direction: Sort.Direction,
    val raw: String,
)

private fun parseSort(sort: String?): ThreadSortSpec {
    val raw = sort?.takeIf { it.isNotBlank() } ?: "createdAt,desc"
    val parts = raw.split(",")
    if (parts.size != 2 || parts[0] != "createdAt") {
        throw BadRequestException("sort must be createdAt,asc or createdAt,desc")
    }
    val direction = when (parts[1].lowercase()) {
        "asc" -> Sort.Direction.ASC
        "desc" -> Sort.Direction.DESC
        else -> throw BadRequestException("sort must be createdAt,asc or createdAt,desc")
    }
    return ThreadSortSpec(property = "createdAt", direction = direction, raw = "createdAt,${parts[1].lowercase()}")
}

private fun Chat.toItemResponse(sources: List<ChatSourceResponse>): ChatItemResponse =
    ChatItemResponse(
        chatId = requireNotNull(id),
        question = question,
        answer = answer,
        model = model,
        createdAt = createdAt,
        sources = sources,
    )
