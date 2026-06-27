package com.example.aichatbot.report

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface ActivityLogRepository : JpaRepository<ActivityLog, Long> {
    fun countByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        type: ActivityType,
        start: Instant,
        end: Instant,
    ): Long

    @Query(
        """
        select c.id as chatId,
               t.id as threadId,
               u.id as userId,
               u.email as userEmail,
               u.name as userName,
               c.model as model,
               c.question as question,
               c.answer as answer,
               c.createdAt as chatCreatedAt
        from Chat c
        join c.thread t
        join c.user u
        where c.deletedAt is null
          and t.deletedAt is null
          and c.createdAt >= :start
          and c.createdAt < :end
        order by c.createdAt asc
        """,
    )
    fun findChatReportRows(@Param("start") start: Instant, @Param("end") end: Instant): List<ChatReportRow>
}

interface ChatReportRow {
    val chatId: Long
    val threadId: Long
    val userId: Long
    val userEmail: String
    val userName: String
    val model: String?
    val question: String
    val answer: String
    val chatCreatedAt: Instant
}
