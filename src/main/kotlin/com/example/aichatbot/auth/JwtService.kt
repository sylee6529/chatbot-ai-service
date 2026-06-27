package com.example.aichatbot.auth

import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    private val properties: JwtProperties,
    private val clock: Clock,
) {
    init {
        require(secretBytes().size >= MIN_SECRET_BYTES) {
            "jwt.secret must be at least $MIN_SECRET_BYTES bytes for HMAC signing"
        }
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secretBytes())
    }

    fun createAccessToken(user: User): String {
        val now = Instant.now(clock)
        val expiresAt = now.plusSeconds(properties.accessTokenTtlSeconds)

        return Jwts.builder()
            .subject(requireNotNull(user.id).toString())
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key)
            .compact()
    }

    fun parseCurrentUser(token: String): CurrentUser {
        val claims = parseClaims(token)
        val id = claims.subject.toLongOrNull() ?: throw JwtAuthenticationException()
        val email = claims["email"] as? String ?: throw JwtAuthenticationException()
        val roleValue = claims["role"] as? String ?: throw JwtAuthenticationException()
        val role = runCatching { Role.valueOf(roleValue) }.getOrElse { throw JwtAuthenticationException() }

        return CurrentUser(id = id, email = email, role = role)
    }

    private fun parseClaims(token: String): Claims =
        runCatching {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload
        }.getOrElse { throw JwtAuthenticationException() }

    private fun secretBytes(): ByteArray = properties.secret.toByteArray(StandardCharsets.UTF_8)
}

class JwtAuthenticationException : RuntimeException()

private const val MIN_SECRET_BYTES = 32
