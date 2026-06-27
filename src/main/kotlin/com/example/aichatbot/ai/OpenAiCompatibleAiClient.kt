package com.example.aichatbot.ai

import com.example.aichatbot.common.ApiException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration
import java.util.Locale

@Component
class OpenAiCompatibleAiClient(
    private val properties: AiProperties,
    restClientBuilder: RestClient.Builder,
) : AiClient {

    private val restClient: RestClient = restClientBuilder
        .baseUrl(properties.baseUrl.trimEnd('/'))
        .requestFactory(requestFactory())
        .build()

    override fun generate(request: AiGenerateRequest): AiGenerateResponse {
        val model = resolveModel(request.model)

        if (properties.apiKey.isBlank()) {
            return AiGenerateResponse(
                answer = fallbackAnswer(request.question),
                model = model,
            )
        }

        val response = callProvider(request, model)
        val answer = response.choices.firstOrNull()?.message?.content?.trim()
            ?: throw AiProviderException("AI provider returned an empty response")

        if (answer.isBlank()) {
            throw AiProviderException("AI provider returned an empty response")
        }

        return AiGenerateResponse(
            answer = answer,
            model = response.model.takeIf { it.isNotBlank() } ?: model,
        )
    }

    private fun resolveModel(modelOverride: String?): String {
        val resolved = modelOverride?.takeIf { it.isNotBlank() } ?: properties.defaultModel
        return resolved.trim()
    }

    private fun fallbackAnswer(question: String): String =
        "AI_API_KEY is not configured. Deterministic fallback answer for: ${question.trim()}"

    private fun callProvider(request: AiGenerateRequest, model: String): ChatCompletionResponse =
        try {
            restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${properties.apiKey}")
                .body(
                    ChatCompletionRequest(
                        model = model,
                        messages = buildMessages(request),
                    ),
                )
                .retrieve()
                .body(ChatCompletionResponse::class.java)
                ?: throw AiProviderException("AI provider returned an empty response")
        } catch (exception: RestClientResponseException) {
            throw AiProviderException("AI provider request failed")
        } catch (exception: ResourceAccessException) {
            throw AiProviderTimeoutException("AI provider request timed out")
        }

    private fun buildMessages(request: AiGenerateRequest): List<ChatMessage> =
        request.context.map { message ->
            ChatMessage(
                role = message.role.name.lowercase(Locale.US),
                content = message.content,
            )
        } + ChatMessage(role = "user", content = request.question)

    private fun requestFactory(): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds))
            setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds))
        }

    private data class ChatCompletionRequest(
        val model: String,
        val messages: List<ChatMessage>,
    )

    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    private data class ChatCompletionResponse(
        val model: String = "",
        val choices: List<ChatChoice> = emptyList(),
    )

    private data class ChatChoice(
        val message: ChatMessage = ChatMessage(role = "assistant", content = ""),
    )
}

class AiProviderException(message: String) : ApiException(HttpStatus.BAD_GATEWAY, message)

class AiProviderTimeoutException(message: String) : ApiException(HttpStatus.GATEWAY_TIMEOUT, message)
