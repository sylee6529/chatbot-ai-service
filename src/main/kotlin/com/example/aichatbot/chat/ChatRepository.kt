package com.example.aichatbot.chat

import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface ChatRepository : JpaRepository<Chat, Long> {
    fun findTopByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId: Long): Optional<Chat>

    fun findByThreadIdAndDeletedAtIsNullOrderByCreatedAtAsc(threadId: Long): List<Chat>

    fun findByThreadIdInAndDeletedAtIsNullOrderByCreatedAtAsc(threadIds: Collection<Long>): List<Chat>

    @org.springframework.data.jpa.repository.Query(
        """
        select c from Chat c
        join c.thread t
        where c.id = :id
          and c.deletedAt is null
          and t.deletedAt is null
        """,
    )
    fun findActiveById(@org.springframework.data.repository.query.Param("id") id: Long): Optional<Chat>
}
