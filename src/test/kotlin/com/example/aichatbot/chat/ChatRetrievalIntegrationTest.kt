package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiGenerateResponse
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class ChatRetrievalIntegrationTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val threadRepository: ChatThreadRepository,
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `member retrieves own threads grouped with chats`() {
        val user = createUser("retrieval-member", Role.MEMBER)
        val other = createUser("retrieval-other", Role.MEMBER)
        val thread = createThreadWithChat(user, "First", Instant.now(clock).minusSeconds(60))
        createChat(thread, user, "Second", Instant.now(clock))
        createThreadWithChat(other, "Other", Instant.now(clock))

        val response = chatService.getThreadChats(user.id!!, Role.MEMBER, null, 0, 10, "createdAt,desc")

        assertEquals(1, response.totalElements)
        assertEquals(thread.id, response.content.single().threadId)
        assertEquals(listOf("First", "Second"), response.content.single().chats.map { it.question })
        assertEquals("createdAt,desc", response.sort)
    }

    @Test
    fun `admin retrieves all threads and filters by user id`() {
        val admin = createUser("retrieval-admin", Role.ADMIN)
        val user = createUser("retrieval-filter", Role.MEMBER)
        val other = createUser("retrieval-filter-other", Role.MEMBER)
        val userThread = createThreadWithChat(user, "User", Instant.now(clock).minusSeconds(30))
        val otherThread = createThreadWithChat(other, "Other", Instant.now(clock))

        val all = chatService.getThreadChats(admin.id!!, Role.ADMIN, null, 0, 10, "createdAt,asc")
        val filtered = chatService.getThreadChats(admin.id!!, Role.ADMIN, user.id, 0, 10, "createdAt,desc")

        val allThreadIds = all.content.map { it.threadId }.toSet()
        assertTrue(allThreadIds.contains(userThread.id))
        assertTrue(allThreadIds.contains(otherThread.id))
        assertEquals(1, filtered.totalElements)
        assertEquals(userThread.id, filtered.content.single().threadId)
        assertEquals(user.id, filtered.content.single().userId)
    }

    @Test
    fun `pagination applies to threads`() {
        val user = createUser("retrieval-page", Role.MEMBER)
        createThreadWithChat(user, "Old", Instant.now(clock).minusSeconds(120))
        val latest = createThreadWithChat(user, "Latest", Instant.now(clock))

        val response = chatService.getThreadChats(user.id!!, Role.MEMBER, null, 0, 1, "createdAt,desc")

        assertEquals(2, response.totalElements)
        assertEquals(2, response.totalPages)
        assertEquals(1, response.size)
        assertEquals(latest.id, response.content.single().threadId)
    }

    private fun createThreadWithChat(user: User, question: String, createdAt: Instant): ChatThread {
        val thread = threadRepository.save(
            ChatThread(user = user, title = question, createdAt = createdAt, updatedAt = createdAt),
        )
        createChat(thread, user, question, createdAt)
        return thread
    }

    private fun createChat(thread: ChatThread, user: User, question: String, createdAt: Instant): Chat =
        chatRepository.save(
            Chat(
                thread = thread,
                user = user,
                question = question,
                answer = "answer: $question",
                model = "test-model",
                createdAt = createdAt,
            ),
        )

    private fun createUser(prefix: String, role: Role): User =
        userRepository.save(
            User(
                email = "$prefix-${UUID.randomUUID()}@example.com",
                passwordHash = passwordEncoder.encode("Pw123456!"),
                name = "Tester",
                role = role,
                createdAt = Instant.now(clock),
            ),
        )

    @TestConfiguration
    class TestAiConfig {
        @Bean
        @Primary
        fun aiClient(): AiClient = object : AiClient {
            override fun generate(request: AiGenerateRequest): AiGenerateResponse =
                AiGenerateResponse(answer = "answer: ${request.question}", model = "test-model")
        }
    }
}
