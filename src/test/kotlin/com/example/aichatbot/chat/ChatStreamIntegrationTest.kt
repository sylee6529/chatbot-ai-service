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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
class ChatStreamIntegrationTest(
    @Autowired private val mockMvc: MockMvc,
) {

    @Test
    fun `streaming chat returns sse and persists final answer`() {
        val email = "stream-${UUID.randomUUID()}@example.com"
        mockMvc.post("/api/v1/auth/signup") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!","name":"Streamer"}"""
        }.andExpect {
            status { isCreated() }
        }
        val login = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"Pw123456!"}"""
        }.andReturn()
        val token = Regex(""""accessToken":"([^"]+)"""").find(login.response.contentAsString)!!.groupValues[1]

        val result = mockMvc.post("/api/v1/chats") {
            contentType = MediaType.APPLICATION_JSON
            accept = MediaType.TEXT_EVENT_STREAM
            header("Authorization", "Bearer $token")
            content = """{"question":"Stream this","isStreaming":true}"""
        }.andExpect {
            request { asyncStarted() }
        }.andReturn()

        mockMvc.perform(asyncDispatch(result))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:chunk")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("event:done")))

        mockMvc.perform(get("/api/v1/chats").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("stream answer for Stream this")))
    }

    @TestConfiguration
    class TestAiConfig {
        @Bean
            @Primary
            fun streamAiClient(): AiClient = object : AiClient {
            override fun generate(request: AiGenerateRequest): AiGenerateResponse {
                Thread.sleep(100)
                return AiGenerateResponse(answer = "stream answer for ${request.question}", model = "stream-model")
            }
        }
    }
}
