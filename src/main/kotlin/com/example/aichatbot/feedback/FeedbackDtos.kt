package com.example.aichatbot.feedback

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class CreateFeedbackRequest(
    @param:JsonProperty("isPositive")
    @get:JsonProperty("isPositive")
    val isPositive: Boolean? = null,
)

data class FeedbackResponse(
    val id: Long,
    val chatId: Long,
    val userId: Long,
    @get:JsonProperty("isPositive")
    val isPositive: Boolean,
    val status: String,
    val createdAt: Instant,
)

data class UpdateFeedbackStatusRequest(
    val status: String? = null,
)

data class UpdateFeedbackStatusResponse(
    val id: Long,
    val status: String,
    val updatedAt: Instant,
)
