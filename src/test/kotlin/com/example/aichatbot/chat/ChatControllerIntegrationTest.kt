package com.example.aichatbot.chat

import com.example.aichatbot.ai.AiClient
import com.example.aichatbot.ai.AiGenerateRequest
import com.example.aichatbot.ai.AiGenerateResponse
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ChatControllerIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `post chats persists question and answer for authenticated user`() {
        val email = "chat-api-${UUID.randomUUID()}@example.com"

        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!","name":"Alice"}"""
        }.andExpect {
            status { isCreated() }
        }

        val login = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!"}"""
        }.andReturn()
        val token = Regex(""""accessToken":"([^"]+)"""").find(login.response.contentAsString)!!.groupValues[1]

        mockMvc.post("/api/v1/chats") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"question":"Hello"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.chatId") { exists() }
            jsonPath("$.threadId") { exists() }
            jsonPath("$.question") { value("Hello") }
            jsonPath("$.answer") { value("api answer: Hello") }
            jsonPath("$.model") { value("api-fake-model") }
            jsonPath("$.createdAt") { exists() }
        }
    }

    @TestConfiguration
    class TestAiConfig {
        @Bean
        @Primary
        fun apiAiClient(): AiClient = object : AiClient {
            override fun generate(request: AiGenerateRequest): AiGenerateResponse =
                AiGenerateResponse(answer = "api answer: ${request.question}", model = "api-fake-model")
        }
    }
}
