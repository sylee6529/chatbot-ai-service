package com.example.aichatbot.ai

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiCompatibleAiClientTest {

    @Test
    fun `generate returns deterministic fallback when api key is blank`() {
        val client = OpenAiCompatibleAiClient(
            properties = AiProperties(
                apiKey = "",
                defaultModel = "default-model",
            ),
            restClientBuilder = RestClient.builder(),
        )

        val response = client.generate(
            AiGenerateRequest(
                question = "Hello?",
                context = listOf(AiContextMessage(AiMessageRole.USER, "Earlier question")),
                model = "override-model",
            ),
        )

        assertEquals("override-model", response.model)
        assertEquals(
            "AI_API_KEY is not configured. Deterministic fallback answer for: Hello?",
            response.answer,
        )
    }

    @Test
    fun `generate posts OpenAI compatible chat completion request`() {
        val requestBody = AtomicReference<String>()
        val authorization = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            authorization.set(exchange.requestHeaders.getFirst("Authorization"))
            requestBody.set(exchange.requestBody.readAllBytes().decodeToString())
            val response = """{"model":"provider-model","choices":[{"message":{"role":"assistant","content":"Next answer"}}]}"""
            val responseBytes = response.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { it.write(responseBytes) }
        }
        server.start()

        try {
            val client = OpenAiCompatibleAiClient(
                properties = AiProperties(
                    apiKey = "test-key",
                    baseUrl = "http://localhost:${server.address.port}/v1",
                    defaultModel = "default-model",
                ),
                restClientBuilder = RestClient.builder(),
            )

            val response = client.generate(
                AiGenerateRequest(
                    question = "Next question",
                    context = listOf(
                        AiContextMessage(AiMessageRole.USER, "First question"),
                        AiContextMessage(AiMessageRole.ASSISTANT, "First answer"),
                    ),
                    model = null,
                ),
            )

            assertEquals("Next answer", response.answer)
            assertEquals("provider-model", response.model)
            assertEquals("Bearer test-key", authorization.get())
            assertTrue(requestBody.get().contains(""""model":"default-model""""))
            assertTrue(requestBody.get().contains(""""role":"user","content":"First question""""))
            assertTrue(requestBody.get().contains(""""role":"assistant","content":"First answer""""))
            assertTrue(requestBody.get().contains(""""role":"user","content":"Next question""""))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `fallback uses default model when override is absent`() {
        val client = OpenAiCompatibleAiClient(
            properties = AiProperties(
                apiKey = "",
                defaultModel = "default-model",
            ),
            restClientBuilder = RestClient.builder(),
        )

        val response = client.generate(AiGenerateRequest("Question", emptyList(), null))

        assertEquals("default-model", response.model)
        assertTrue(response.answer.contains("Question"))
    }
}
