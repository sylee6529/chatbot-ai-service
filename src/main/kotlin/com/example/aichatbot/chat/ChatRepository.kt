package com.example.aichatbot.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ChatRepository : JpaRepository<Chat, Long> {
    fun findTopByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId: Long): Optional<Chat>

    fun findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId: Long): List<Chat>

    fun findByThreadIdInAndDeletedAtIsNullOrderByCreatedAtAsc(threadIds: Collection<Long>): List<Chat>
}
