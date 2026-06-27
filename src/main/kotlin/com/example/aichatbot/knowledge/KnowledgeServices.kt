package com.example.aichatbot.knowledge

import com.example.aichatbot.ai.AiContextMessage
import com.example.aichatbot.ai.AiMessageRole
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import kotlin.math.sqrt

data class RetrievedKnowledgeSource(
    val chunkId: Long,
    val documentTitle: String,
    val chunkIndex: Int,
    val content: String,
    val score: Double,
)

data class KnowledgeContext(
    val messages: List<AiContextMessage>,
    val sources: List<RetrievedKnowledgeSource>,
)

data class ChatSourceResponse(
    val documentTitle: String,
    val chunkIndex: Int,
)

@Service
class KnowledgeContextService(
    private val embeddingClient: EmbeddingClient,
    private val chunkRepository: DocumentChunkRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun retrieve(question: String, enabled: Boolean, topK: Int = DEFAULT_TOP_K): KnowledgeContext {
        if (!enabled) {
            return KnowledgeContext(emptyList(), emptyList())
        }

        val chunks = chunkRepository.findByDeletedAtIsNullAndEmbeddingModelOrderByCreatedAtDesc(embeddingClient.model)
        if (chunks.isEmpty()) {
            log.debug("No demo knowledge chunks found for embedding model={}", embeddingClient.model)
            return KnowledgeContext(emptyList(), emptyList())
        }

        val questionEmbedding = embeddingClient.embed(question)
        val sources = chunks.asSequence()
            .mapNotNull { chunk ->
                val embedding = chunk.embedding?.let(::parseVector) ?: return@mapNotNull null
                val score = cosineSimilarity(questionEmbedding, embedding)
                RetrievedKnowledgeSource(
                    chunkId = requireNotNull(chunk.id),
                    documentTitle = chunk.document.title,
                    chunkIndex = chunk.chunkIndex,
                    content = chunk.content,
                    score = score,
                )
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()

        if (sources.isEmpty()) {
            return KnowledgeContext(emptyList(), emptyList())
        }

        return KnowledgeContext(
            messages = listOf(AiContextMessage(role = AiMessageRole.SYSTEM, content = buildContextPrompt(sources))),
            sources = sources,
        )
    }

    private fun buildContextPrompt(sources: List<RetrievedKnowledgeSource>): String {
        val context = sources.joinToString(separator = "\n\n") { source ->
            "[문서: ${source.documentTitle} / chunk ${source.chunkIndex}]\n${source.content}"
        }
        return """
            너는 고객사 문서를 기반으로 답변하는 AI assistant다.
            아래 CONTEXT에 관련 문서 내용이 있으면 우선적으로 참고하라.
            CONTEXT에 없는 내용은 추측하지 말고, 문서에서 확인되지 않는다고 말하라.

            CONTEXT:
            $context
        """.trimIndent()
    }

    companion object {
        const val DEFAULT_TOP_K = 3
    }
}

@Service
class DocumentChunker {
    fun chunk(content: String, maxChars: Int = DEFAULT_MAX_CHARS): List<String> =
        content.trim()
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .flatMap { paragraph ->
                if (paragraph.length <= maxChars) {
                    listOf(paragraph)
                } else {
                    paragraph.chunked(maxChars)
                }
            }

    companion object {
        private const val DEFAULT_MAX_CHARS = 900
    }
}

@Component
class DemoKnowledgeSeeder(
    private val documentRepository: DemoDocumentRepository,
    private val chunkRepository: DocumentChunkRepository,
    private val chunker: DocumentChunker,
    private val embeddingClient: EmbeddingClient,
    private val clock: Clock,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val now = Instant.now(clock)
        val document = documentRepository.findBySourceKeyAndDeletedAtIsNull(SOURCE_KEY)
            .orElseGet {
                documentRepository.save(
                    DemoDocument(
                        title = TITLE,
                        sourceKey = SOURCE_KEY,
                        createdAt = now,
                    ),
                )
            }

        val existingChunks = chunkRepository.findByDocumentSourceKeyAndDeletedAtIsNullOrderByChunkIndexAsc(SOURCE_KEY)
        val expectedChunks = chunker.chunk(CONTENT)
        val needsRebuild = existingChunks.size != expectedChunks.size ||
            existingChunks.any { it.embeddingModel != embeddingClient.model } ||
            existingChunks.zip(expectedChunks).any { (stored, expected) -> stored.content != expected }

        if (!needsRebuild) {
            return
        }

        expectedChunks.forEachIndexed { index, content ->
            val chunkIndex = index + 1
            val chunk = existingChunks.firstOrNull { it.chunkIndex == chunkIndex }
                ?: DocumentChunk(
                    document = document,
                    chunkIndex = chunkIndex,
                    content = content,
                    createdAt = now,
                )
            chunk.content = content
            chunk.embedding = embeddingClient.embed(content).toVectorText()
            chunk.embeddingModel = embeddingClient.model
            chunk.deletedAt = null
            chunkRepository.save(chunk)
        }
        existingChunks
            .filter { it.chunkIndex > expectedChunks.size }
            .forEach {
                it.deletedAt = now
                chunkRepository.save(it)
            }
        log.info("Demo knowledge seeded with {} chunks using embeddingModel={}", expectedChunks.size, embeddingClient.model)
    }

    companion object {
        const val SOURCE_KEY = "aichatbot-service-description"
        const val TITLE = "AIChatbot 서비스 설명"

        private val CONTENT = """
            AIChatbot은 Kotlin 1.9와 Spring Boot 3 기반의 AI 챗봇 백엔드 서비스다. 사용자는 이메일, 비밀번호, 이름으로 회원가입하고 이메일과 비밀번호로 로그인한다. 로그인 성공 시 JWT access token을 발급받으며, 가입과 로그인을 제외한 API는 JWT 인증을 요구한다.

            채팅 API는 사용자의 질문을 AI provider에 전달하고 질문과 답변을 PostgreSQL에 저장한다. 사용자의 최신 채팅이 30분 이내이면 같은 스레드를 재사용하고, 30분을 초과했거나 첫 질문이면 새 스레드를 만든다. 같은 스레드의 이전 질문과 답변은 다음 AI 요청의 대화 context로 사용된다.

            사용자는 스레드별로 그룹핑된 채팅 목록을 조회할 수 있고, 본인의 스레드를 삭제할 수 있다. 관리자는 전체 사용자의 채팅을 조회할 수 있다. 삭제는 소프트 삭제로 처리되어 일반 조회, AI context, 리포트에서 제외된다.

            피드백 기능은 사용자가 각 채팅 답변에 긍정 또는 부정 피드백을 남기는 기능이다. 한 사용자는 같은 채팅에 피드백을 한 번만 남길 수 있다. 관리자는 피드백 목록을 확인하고 pending 또는 resolved 상태로 처리할 수 있다.

            관리자 API는 최근 24시간 동안의 회원가입 수, 성공 로그인 수, 채팅 생성 수를 집계한다. 또한 최근 24시간 채팅 목록을 CSV로 내려받을 수 있으며 CSV에는 채팅 id, 스레드 id, 사용자 이메일, 사용자 이름, 모델, 질문, 답변, 생성 시각이 포함된다.

            스트리밍 채팅은 isStreaming=true와 Accept: text/event-stream 요청에서 Server-Sent Events 방식으로 답변 chunk를 전달한다. 스트리밍이 완료되면 조립된 전체 답변도 일반 채팅과 동일하게 저장된다.

            Demo Knowledge Base는 고객사의 향후 내부 문서 기반 답변 니즈를 보여주기 위한 시연 기능이다. 문서를 모델에 직접 학습시키는 fine-tuning이 아니라, 사전 등록된 서비스 설명 문서 chunk를 검색해 AI 요청 context에 포함하는 RAG 방식이다.
        """.trimIndent()
    }
}

fun List<Double>.toVectorText(): String = joinToString(prefix = "[", postfix = "]", separator = ",")

fun parseVector(value: String): List<Double> =
    value.trim()
        .removePrefix("[")
        .removeSuffix("]")
        .split(",")
        .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toDoubleOrNull() }

fun cosineSimilarity(left: List<Double>, right: List<Double>): Double {
    if (left.isEmpty() || right.isEmpty() || left.size != right.size) {
        return 0.0
    }
    val dot = left.indices.sumOf { left[it] * right[it] }
    val leftMagnitude = sqrt(left.sumOf { it * it })
    val rightMagnitude = sqrt(right.sumOf { it * it })
    if (leftMagnitude == 0.0 || rightMagnitude == 0.0) {
        return 0.0
    }
    return dot / (leftMagnitude * rightMagnitude)
}
