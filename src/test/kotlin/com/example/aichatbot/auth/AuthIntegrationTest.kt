package com.example.aichatbot.auth

import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.blankOrNullString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.get
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `signup creates member and duplicate email returns conflict`() {
        val body = """{"email":"auth-signup@example.com","password":"Pw123456!","name":"Alice"}"""

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value("auth-signup@example.com") }
            jsonPath("$.name") { value("Alice") }
            jsonPath("$.role") { value("MEMBER") }
            jsonPath("$.createdAt") { exists() }
        }

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andExpect {
            status { isConflict() }
            jsonPath("$.message") { value("Email already exists") }
        }
    }

    @Test
    fun `login issues jwt and protected routes require token`() {
        val signupBody = """{"email":"auth-login@example.com","password":"Pw123456!","name":"Alice"}"""

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = signupBody
        }.andExpect {
            status { isCreated() }
        }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"auth-login@example.com","password":"Pw123456!"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.tokenType") { value("Bearer") }
            jsonPath("$.accessToken") { value(not(blankOrNullString())) }
            jsonPath("$.user.email") { value("auth-login@example.com") }
            jsonPath("$.user.role") { value("MEMBER") }
        }

        mockMvc.get("/api/v1/chats")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.message") { value("Missing or invalid token") }
            }
    }
}
