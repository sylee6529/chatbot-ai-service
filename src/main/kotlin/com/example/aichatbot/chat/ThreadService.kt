package com.example.aichatbot.chat

import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.common.NotFoundException
import com.example.aichatbot.feedback.FeedbackRepository
import com.example.aichatbot.user.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class ThreadService(
    private val threadRepository: ChatThreadRepository,
    private val chatRepository: ChatRepository,
    private val feedbackRepository: FeedbackRepository,
    private val clock: Clock,
) {

    @Transactional
    fun deleteThread(currentUserId: Long, currentUserRole: Role, threadId: Long) {
        val thread = threadRepository.findByIdAndDeletedAtIsNull(threadId)
            .orElseThrow { NotFoundException("Thread not found") }
        if (currentUserRole != Role.ADMIN && thread.user.id != currentUserId) {
            throw ForbiddenException()
        }

        val now = Instant.now(clock)
        thread.deletedAt = now
        val chats = chatRepository.findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId)
        chats.forEach { it.deletedAt = now }
        val chatIds = chats.mapNotNull { it.id }
        if (chatIds.isNotEmpty()) {
            feedbackRepository.findByChatIdInAndDeletedAtIsNull(chatIds).forEach { it.softDelete(now) }
        }
    }
}
