package com.example.aichatbot.user

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun existsByEmail(email: String): Boolean

    fun findByEmailAndDeletedAtIsNull(email: String): Optional<User>

    fun findByIdAndDeletedAtIsNull(id: Long): Optional<User>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id and u.deletedAt is null")
    fun findActiveByIdForUpdate(@Param("id") id: Long): Optional<User>
}
