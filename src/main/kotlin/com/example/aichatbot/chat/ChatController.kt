package com.example.aichatbot.chat

import com.example.aichatbot.auth.CurrentUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val chatService: ChatService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createChat(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Valid @RequestBody request: CreateChatRequest,
    ): CreateChatResponse = chatService.createChat(currentUser.id, request)
}
