package com.example.aichatbot.chat

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class CreateChatRequest(
    val question: String? = null,

    val model: String? = null,

    @param:JsonProperty("isStreaming")
    @field:JsonProperty("isStreaming")
    val streaming: Boolean = false,
)

data class CreateChatResponse(
    val chatId: Long,
    val threadId: Long,
    val question: String,
    val answer: String,
    val model: String,
    val createdAt: Instant,
)

data class ThreadChatsResponse(
    val threadId: Long,
    val userId: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
    val chats: List<ChatItemResponse>,
)

data class ChatItemResponse(
    val chatId: Long,
    val question: String,
    val answer: String,
    val model: String?,
    val createdAt: Instant,
)
