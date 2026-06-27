package com.example.aichatbot.common

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ErrorEnvelopeIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `invalid query parameter returns api error envelope`() {
        val token = memberToken()

        mockMvc.get("/api/v1/chats?page=abc") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.status") { value(400) }
            jsonPath("$.error") { value("Bad Request") }
            jsonPath("$.message") { value("Invalid request parameter") }
            jsonPath("$.path") { value("/api/v1/chats") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    @Test
    fun `unexpected exception returns generic api error envelope`() {
        val token = memberToken()

        mockMvc.get("/test/unexpected-error") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isInternalServerError() }
            jsonPath("$.status") { value(500) }
            jsonPath("$.error") { value("Internal Server Error") }
            jsonPath("$.message") { value("Unexpected server error") }
            jsonPath("$.path") { value("/test/unexpected-error") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    private fun memberToken(): String {
        val email = "error-${UUID.randomUUID()}@example.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!","name":"Error User"}"""
        }.andExpect {
            status { isCreated() }
        }

        val login = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!"}"""
        }.andReturn()

        return Regex(""""accessToken":"([^"]+)"""").find(login.response.contentAsString)!!.groupValues[1]
    }

    @TestConfiguration
    class ErrorControllerConfig {
        @Bean
        fun testErrorController(): TestErrorController = TestErrorController()
    }

    @RestController
    class TestErrorController {
        @GetMapping("/test/unexpected-error")
        fun unexpected(): String {
            error("sensitive detail")
        }
    }
}
