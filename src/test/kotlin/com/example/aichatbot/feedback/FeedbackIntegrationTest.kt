package com.example.aichatbot.feedback

import com.example.aichatbot.chat.Chat
import com.example.aichatbot.chat.ChatRepository
import com.example.aichatbot.chat.ChatThread
import com.example.aichatbot.chat.ChatThreadRepository
import com.example.aichatbot.common.ConflictException
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Transactional
class FeedbackIntegrationTest(
    @Autowired private val feedbackService: FeedbackService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val threadRepository: ChatThreadRepository,
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `member creates one feedback for own chat`() {
        val user = createUser("feedback-member", Role.MEMBER)
        val chat = createChat(user, "Question")

        val created = feedbackService.createFeedback(user.id!!, Role.MEMBER, chat.id!!, CreateFeedbackRequest(true))

        assertEquals(chat.id, created.chatId)
        assertEquals(user.id, created.userId)
        assertEquals(true, created.isPositive)
        assertEquals("pending", created.status)
        assertFailsWith<ConflictException> {
            feedbackService.createFeedback(user.id!!, Role.MEMBER, chat.id!!, CreateFeedbackRequest(false))
        }
    }

    @Test
    fun `member cannot create feedback for another user's chat`() {
        val owner = createUser("feedback-owner", Role.MEMBER)
        val other = createUser("feedback-other", Role.MEMBER)
        val chat = createChat(owner, "Question")

        assertFailsWith<ForbiddenException> {
            feedbackService.createFeedback(other.id!!, Role.MEMBER, chat.id!!, CreateFeedbackRequest(true))
        }
    }

    @Test
    fun `feedback list is scoped by role and filter`() {
        val user = createUser("feedback-list-user", Role.MEMBER)
        val other = createUser("feedback-list-other", Role.MEMBER)
        val admin = createUser("feedback-list-admin", Role.ADMIN)
        feedbackService.createFeedback(user.id!!, Role.MEMBER, createChat(user, "Good").id!!, CreateFeedbackRequest(true))
        feedbackService.createFeedback(other.id!!, Role.MEMBER, createChat(other, "Bad").id!!, CreateFeedbackRequest(false))

        val memberList = feedbackService.getFeedbacks(user.id!!, Role.MEMBER, null, null, 0, 20, "createdAt,desc")
        val adminPositive = feedbackService.getFeedbacks(admin.id!!, Role.ADMIN, true, "pending", 0, 20, "createdAt,desc")

        assertEquals(1, memberList.totalElements)
        assertEquals(user.id, memberList.content.single().userId)
        assertEquals(1, adminPositive.totalElements)
        assertEquals(true, adminPositive.content.single().isPositive)
    }

    @Test
    fun `admin updates feedback status`() {
        val user = createUser("feedback-status-user", Role.MEMBER)
        val admin = createUser("feedback-status-admin", Role.ADMIN)
        val feedback = feedbackService.createFeedback(user.id!!, Role.MEMBER, createChat(user, "Question").id!!, CreateFeedbackRequest(true))

        assertFailsWith<ForbiddenException> {
            feedbackService.updateStatus(Role.MEMBER, feedback.id, UpdateFeedbackStatusRequest("resolved"))
        }
        val updated = feedbackService.updateStatus(Role.ADMIN, feedback.id, UpdateFeedbackStatusRequest("resolved"))

        assertEquals(admin.role, Role.ADMIN)
        assertEquals("resolved", updated.status)
    }

    private fun createChat(user: User, question: String): Chat {
        val now = Instant.now(clock)
        val thread = threadRepository.save(ChatThread(user = user, title = question, createdAt = now, updatedAt = now))
        return chatRepository.save(
            Chat(
                thread = thread,
                user = user,
                question = question,
                answer = "answer",
                model = "test-model",
                createdAt = now,
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
