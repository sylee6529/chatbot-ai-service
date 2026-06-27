package com.example.aichatbot.ai

interface AiClient {
    fun generate(request: AiGenerateRequest): AiGenerateResponse
}

data class AiGenerateRequest(
    val question: String,
    val context: List<AiContextMessage>,
    val model: String?,
)

data class AiContextMessage(
    val role: AiMessageRole,
    val content: String,
)

enum class AiMessageRole {
    USER,
    ASSISTANT,
}

data class AiGenerateResponse(
    val answer: String,
    val model: String,
)
