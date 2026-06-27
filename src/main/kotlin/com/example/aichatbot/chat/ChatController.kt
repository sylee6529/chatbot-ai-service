package com.example.aichatbot.chat

import com.example.aichatbot.auth.CurrentUser
import com.example.aichatbot.common.PageResponse
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
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

    @GetMapping
    fun getChats(
        @AuthenticationPrincipal currentUser: CurrentUser,
        page: Int = 0,
        size: Int = 20,
        sort: String? = null,
        userId: Long? = null,
    ): PageResponse<ThreadChatsResponse> =
        chatService.getThreadChats(
            currentUserId = currentUser.id,
            currentUserRole = currentUser.role,
            requestedUserId = userId,
            page = page,
            size = size,
            sort = sort,
        )

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createChat(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Valid @RequestBody request: CreateChatRequest,
    ): CreateChatResponse = chatService.createChat(currentUser.id, request)
}
