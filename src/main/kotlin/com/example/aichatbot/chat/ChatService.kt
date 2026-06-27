package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiContextMessage
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiMessageRole
import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.NotFoundException
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class ChatService(
    private val userRepository: UserRepository,
    private val threadRepository: ChatThreadRepository,
    private val chatRepository: ChatRepository,
    private val aiClient: AiClient,
    private val clock: Clock,
    transactionManager: PlatformTransactionManager,
    @Value("\${chat.thread.window-minutes:30}") private val threadWindowMinutes: Long,
) {
    private val transactionTemplate = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    fun createChat(userId: Long, request: CreateChatRequest): CreateChatResponse {
        if (request.isStreaming) {
            throw BadRequestException("streaming is not supported yet")
        }

        val question = request.question?.trim().orEmpty()
        if (question.isBlank()) {
            throw BadRequestException("question must not be blank")
        }

        val model = normalizeModel(request.model)
        val context = loadContextSnapshot(userId)
        val aiResponse = aiClient.generate(
            AiGenerateRequest(
                question = question,
                context = context,
                model = model,
            ),
        )

        return saveChat(userId, question, aiResponse.answer, aiResponse.model)
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

    private fun saveChat(userId: Long, question: String, answer: String, model: String): CreateChatResponse =
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

                CreateChatResponse(
                    chatId = requireNotNull(chat.id),
                    threadId = requireNotNull(thread.id),
                    question = chat.question,
                    answer = chat.answer,
                    model = requireNotNull(chat.model),
                    createdAt = chat.createdAt,
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
