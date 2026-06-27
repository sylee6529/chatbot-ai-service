package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiGenerateResponse
import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.ForbiddenException
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
import kotlin.test.assertFailsWith

@SpringBootTest
@Transactional
class ChatAuthorizationIntegrationTest(
    @Autowired private val chatService: ChatService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `member cannot filter another user's chats`() {
        val member = createUser("auth-member", Role.MEMBER)
        val other = createUser("auth-other", Role.MEMBER)

        assertFailsWith<ForbiddenException> {
            chatService.getThreadChats(member.id!!, Role.MEMBER, other.id, 0, 20, "createdAt,desc")
        }
    }

    @Test
    fun `invalid pagination and sort are rejected`() {
        val member = createUser("auth-invalid", Role.MEMBER)

        assertFailsWith<BadRequestException> {
            chatService.getThreadChats(member.id!!, Role.MEMBER, null, -1, 20, "createdAt,desc")
        }
        assertFailsWith<BadRequestException> {
            chatService.getThreadChats(member.id!!, Role.MEMBER, null, 0, 101, "createdAt,desc")
        }
        assertFailsWith<BadRequestException> {
            chatService.getThreadChats(member.id!!, Role.MEMBER, null, 0, 20, "updatedAt,desc")
        }
    }

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
