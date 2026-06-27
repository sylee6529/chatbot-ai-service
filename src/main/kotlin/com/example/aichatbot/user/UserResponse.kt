package com.example.aichatbot.user

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class UserResponse(
    val id: Long,
    val email: String,
    val name: String,
    val role: Role,
    val createdAt: Instant? = null,
)

fun User.toResponse(includeCreatedAt: Boolean = true): UserResponse =
    UserResponse(
        id = requireNotNull(id),
        email = email,
        name = name,
        role = role,
        createdAt = createdAt.takeIf { includeCreatedAt },
    )
