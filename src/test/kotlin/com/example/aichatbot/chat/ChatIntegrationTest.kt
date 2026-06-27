package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiGenerateResponse
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@SpringBootTest
class ChatIntegrationTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val threadRepository: ChatThreadRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val aiClient: CapturingAiClient,
    @Autowired private val clock: Clock,
) {

    @BeforeEach
    fun resetAiClient() {
        aiClient.requests.clear()
    }

    @Test
    fun `create chat creates new thread then reuses it within thirty minutes`() {
        val user = createUser("thread-reuse")

        val first = chatService.createChat(user.id!!, CreateChatRequest(question = "Hello"))
        val second = chatService.createChat(user.id!!, CreateChatRequest(question = "Again"))

        assertEquals(first.threadId, second.threadId)
        assertEquals(
            listOf("Hello", "Again"),
            chatRepository.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(first.threadId).map { it.question },
        )
        assertEquals(2, aiClient.requests.size)
        assertEquals(2, aiClient.requests.last().context.size)
        assertEquals("Hello", aiClient.requests.last().context[0].content)
        assertEquals("answer: Hello", aiClient.requests.last().context[1].content)
    }

    @Test
    fun `create chat creates a new thread when latest chat is older than thirty minutes`() {
        val user = createUser("thread-new")
        val oldThread = threadRepository.save(
            ChatThread(
                user = user,
                title = "Old",
                createdAt = Instant.now(clock).minusSeconds(1_900),
                updatedAt = Instant.now(clock).minusSeconds(1_900),
            ),
        )
        chatRepository.save(
            Chat(
                thread = oldThread,
                user = user,
                question = "Old question",
                answer = "Old answer",
                model = "fake-model",
                createdAt = Instant.now(clock).minusSeconds(1_900),
            ),
        )

        val created = chatService.createChat(user.id!!, CreateChatRequest(question = "New question"))

        assertNotEquals(oldThread.id, created.threadId)
        assertEquals(0, aiClient.requests.single().context.size)
    }

    @Test
    fun `create chat passes model override to ai client and stores response`() {
        val user = createUser("chat-model")

        val response = chatService.createChat(
            user.id!!,
            CreateChatRequest(question = "Use model", model = "gpt-test"),
        )

        assertEquals("gpt-test", aiClient.requests.single().model)
        assertEquals("fake-model:gpt-test", response.model)
        assertEquals("answer: Use model", response.answer)
    }

    private fun createUser(prefix: String): User =
        userRepository.save(
            User(
                email = "$prefix-${UUID.randomUUID()}@example.com",
                passwordHash = passwordEncoder.encode("Pw123456!"),
                name = "Tester",
                role = Role.MEMBER,
                createdAt = Instant.now(clock),
            ),
        )

    @TestConfiguration
    class TestAiConfig {
        @Bean
        @Primary
        fun capturingAiClient(): CapturingAiClient = CapturingAiClient()
    }
}

class CapturingAiClient : AiClient {
    val requests = mutableListOf<AiGenerateRequest>()

    override fun generate(request: AiGenerateRequest): AiGenerateResponse {
        requests += request
        return AiGenerateResponse(
            answer = "answer: ${request.question}",
            model = request.model?.let { "fake-model:$it" } ?: "fake-model",
        )
    }
}
