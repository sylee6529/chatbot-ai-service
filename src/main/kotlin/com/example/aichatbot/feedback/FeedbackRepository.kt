package com.example.aichatbot.feedback

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface FeedbackRepository : JpaRepository<Feedback, Long> {
    fun existsByUserIdAndChatId(userId: Long, chatId: Long): Boolean

    fun findByChatIdInAndDeletedAtIsNull(chatIds: Collection<Long>): List<Feedback>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<Feedback>

    @Query(
        """
        select f from Feedback f
        where f.deletedAt is null
          and (:userId is null or f.user.id = :userId)
          and (:isPositive is null or f.isPositive = :isPositive)
          and (:status is null or f.status = :status)
        """,
    )
    fun findActiveFeedbacks(
        @Param("userId") userId: Long?,
        @Param("isPositive") isPositive: Boolean?,
        @Param("status") status: FeedbackStatus?,
        pageable: Pageable,
    ): Page<Feedback>
}
