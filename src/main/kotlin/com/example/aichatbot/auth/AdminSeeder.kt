package com.example.aichatbot.auth

import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Component
class AdminSeeder(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock,
    @Value("\${ADMIN_EMAIL:}") private val adminEmail: String,
    @Value("\${ADMIN_PASSWORD:}") private val adminPassword: String,
    @Value("\${ADMIN_NAME:Admin}") private val adminName: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val email = adminEmail.trim().lowercase()
        val password = adminPassword.trim()

        if (email.isBlank() || password.isBlank()) {
            return
        }

        if (userRepository.existsByEmail(email)) {
            return
        }

        userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode(password),
                name = adminName.trim().ifBlank { "Admin" },
                role = Role.ADMIN,
                createdAt = Instant.now(clock),
            ),
        )
        log.info("Initial admin account seeded for email={}", email)
    }
}
