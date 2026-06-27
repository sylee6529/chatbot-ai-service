package com.example.aichatbot.chat

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface ChatThreadRepository : JpaRepository<ChatThread, Long> {
    @Query(
        """
        select t from ChatThread t
        where t.deletedAt is null
          and (:userId is null or t.user.id = :userId)
        """,
    )
    fun findActiveThreads(@Param("userId") userId: Long?, pageable: Pageable): Page<ChatThread>
}
