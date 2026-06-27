package com.example.aichatbot.ai

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ai")
data class AiProperties(
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val defaultModel: String = "gpt-4o-mini",
    val embeddingModel: String = "text-embedding-3-small",
    val connectTimeoutSeconds: Long = 5,
    val readTimeoutSeconds: Long = 30,
)
