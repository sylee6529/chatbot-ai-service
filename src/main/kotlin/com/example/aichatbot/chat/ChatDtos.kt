package com.example.aichatbot.chat

import java.time.Instant

data class CreateChatRequest(
    val question: String? = null,

    val model: String? = null,

    val isStreaming: Boolean = false,
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
