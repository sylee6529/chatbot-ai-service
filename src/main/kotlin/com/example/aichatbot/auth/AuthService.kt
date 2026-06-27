package com.example.aichatbot.auth

import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.ConflictException
import com.example.aichatbot.common.UnauthorizedException
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import com.example.aichatbot.user.UserResponse
import com.example.aichatbot.user.toResponse
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val clock: Clock,
) {

    @Transactional
    fun signup(request: SignupRequest): UserResponse {
        val email = normalizeEmail(request.email)
        validatePasswordStrength(request.password)

        if (userRepository.existsByEmail(email)) {
            throw ConflictException("Email already exists")
        }

        val user = userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(request.password),
                name = request.name.trim(),
                role = Role.MEMBER,
                createdAt = Instant.now(clock),
            ),
        )

        return user.toResponse()
    }

    @Transactional
    fun login(request: LoginRequest): LoginResponse {
        val email = normalizeEmail(request.email)
        val user = userRepository.findByEmailAndDeletedAtIsNull(email)
            .orElseThrow { UnauthorizedException("Invalid email or password") }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw UnauthorizedException("Invalid email or password")
        }

        return LoginResponse(
            accessToken = jwtService.createAccessToken(user),
            user = user.toResponse(includeCreatedAt = false),
        )
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun validatePasswordStrength(password: String) {
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            throw BadRequestException("password must contain letters and numbers")
        }
    }
}
