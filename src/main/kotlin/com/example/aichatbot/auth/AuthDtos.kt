package com.example.aichatbot.auth

import com.example.aichatbot.user.UserResponse
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class SignupRequest(
    @field:NotBlank(message = "email must not be blank")
    @field:Email(message = "email must be valid")
    val email: String,

    @field:NotBlank(message = "password must not be blank")
    @field:Size(min = 8, message = "password must be at least 8 characters")
    val password: String,

    @field:NotBlank(message = "name must not be blank")
    @field:Size(max = 100, message = "name must be at most 100 characters")
    val name: String,
)

data class LoginRequest(
    @field:NotBlank(message = "email must not be blank")
    @field:Email(message = "email must be valid")
    val email: String,

    @field:NotBlank(message = "password must not be blank")
    val password: String,
)

data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val user: UserResponse,
)
