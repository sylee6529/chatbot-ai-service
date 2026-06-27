package com.example.aichatbot.report

import com.example.aichatbot.chat.Chat
import com.example.aichatbot.chat.ChatRepository
import com.example.aichatbot.chat.ChatThread
import com.example.aichatbot.chat.ChatThreadRepository
import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@SpringBootTest
@Transactional
class ReportIntegrationTest(
    @Autowired private val reportService: ReportService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val threadRepository: ChatThreadRepository,
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `admin gets recent chat csv excluding deleted chats`() {
        val user = createUser("report-user", Role.MEMBER)
        createChat(user, "Included", null, null)
        createChat(user, "Deleted chat", null, Instant.now(clock))
        createChat(user, "Old chat", Instant.now(clock).minusSeconds(90_000), null)

        val csv = reportService.getChatsCsv(Role.ADMIN)

        assertTrue(csv.startsWith("\uFEFFchat_id,thread_id,user_id,user_email,user_name,model,question,answer,chat_created_at"))
        assertTrue(csv.contains("\"Included\""))
        assertFalse(csv.contains("\"Deleted chat\""))
        assertFalse(csv.contains("\"Old chat\""))
    }

    @Test
    fun `csv report escapes formula injection values`() {
        val user = createUser("report-formula", Role.MEMBER)
        createChat(user, "=HYPERLINK(\"https://example.com\")", null, null)
        createChat(user, "+cmd", null, null)
        createChat(user, "-2+3", null, null)
        createChat(user, "@SUM(1,2)", null, null)

        val csv = reportService.getChatsCsv(Role.ADMIN)

        assertTrue(csv.contains("\"'=HYPERLINK(\"\"https://example.com\"\")\""))
        assertTrue(csv.contains("\"'+cmd\""))
        assertTrue(csv.contains("\"'-2+3\""))
        assertTrue(csv.contains("\"'@SUM(1,2)\""))
    }

    @Test
    fun `member cannot get csv report`() {
        assertFailsWith<ForbiddenException> {
            reportService.getChatsCsv(Role.MEMBER)
        }
    }

    private fun createChat(user: User, question: String, createdAtOverride: Instant?, deletedAt: Instant?) {
        val now = createdAtOverride ?: Instant.now(clock)
        val thread = threadRepository.save(ChatThread(user = user, title = question, createdAt = now, updatedAt = now))
        chatRepository.save(
            Chat(
                thread = thread,
                user = user,
                question = question,
                answer = "answer: $question",
                model = "test-model",
                createdAt = now,
                deletedAt = deletedAt,
            ),
        )
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
}
