package com.example.aichatbot.report

import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `csv report returns utf eight content type and bom`() {
        val token = adminToken()

        mockMvc.get("/api/v1/admin/reports/chats.csv") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            header { string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8") }
            content { string(org.hamcrest.Matchers.startsWith("\uFEFFchat_id")) }
        }
    }

    private fun adminToken(): String {
        val email = "csv-admin-${UUID.randomUUID()}@example.com"
        userRepository.save(
            User(
                email = email,
                passwordHash = passwordEncoder.encode("Admin1234!"),
                name = "CSV Admin",
                role = Role.ADMIN,
                createdAt = Instant.now(clock),
            ),
        )

        val login = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Admin1234!"}"""
        }.andReturn()

        return Regex(""""accessToken":"([^"]+)"""").find(login.response.contentAsString)!!.groupValues[1]
    }
}
