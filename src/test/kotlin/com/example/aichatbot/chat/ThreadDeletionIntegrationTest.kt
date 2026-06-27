package com.example.aichatbot.chat

import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.common.NotFoundException
import com.example.aichatbot.feedback.CreateFeedbackRequest
import com.example.aichatbot.feedback.FeedbackRepository
import com.example.aichatbot.feedback.FeedbackService
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

@SpringBootTest
@Transactional
class ThreadDeletionIntegrationTest(
    @Autowired private val threadService: ThreadService,
    @Autowired private val feedbackService: FeedbackService,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val threadRepository: ChatThreadRepository,
    @Autowired private val chatRepository: ChatRepository,
    @Autowired private val feedbackRepository: FeedbackRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `owner deletes thread with chats and feedbacks softly`() {
        val user = createUser("delete-owner", Role.MEMBER)
        val chat = createThreadWithChat(user)
        val feedback = feedbackService.createFeedback(user.id!!, Role.MEMBER, chat.id!!, CreateFeedbackRequest(true))

        threadService.deleteThread(user.id!!, Role.MEMBER, chat.thread.id!!)

        assertNotNull(threadRepository.findById(chat.thread.id!!).orElseThrow().deletedAt)
        assertNotNull(chatRepository.findById(chat.id!!).orElseThrow().deletedAt)
        assertNotNull(feedbackRepository.findById(feedback.id).orElseThrow().deletedAt)
        assertFailsWith<NotFoundException> {
            threadService.deleteThread(user.id!!, Role.MEMBER, chat.thread.id!!)
        }
    }

    @Test
    fun `non owner cannot delete thread`() {
        val owner = createUser("delete-owner2", Role.MEMBER)
        val other = createUser("delete-other", Role.MEMBER)
        val chat = createThreadWithChat(owner)

        assertFailsWith<ForbiddenException> {
            threadService.deleteThread(other.id!!, Role.MEMBER, chat.thread.id!!)
        }
    }

    @Test
    fun `admin can delete any thread`() {
        val owner = createUser("delete-owner3", Role.MEMBER)
        val admin = createUser("delete-admin", Role.ADMIN)
        val chat = createThreadWithChat(owner)

        threadService.deleteThread(admin.id!!, Role.ADMIN, chat.thread.id!!)

        assertNotNull(threadRepository.findById(chat.thread.id!!).orElseThrow().deletedAt)
    }

    private fun createThreadWithChat(user: User): Chat {
        val now = Instant.now(clock)
        val thread = threadRepository.save(ChatThread(user = user, title = "Question", createdAt = now, updatedAt = now))
        return chatRepository.save(
            Chat(
                thread = thread,
                user = user,
                question = "Question",
                answer = "Answer",
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
