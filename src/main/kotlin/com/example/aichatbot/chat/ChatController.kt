package com.example.aichatbot.chat

import com.example.aichatbot.auth.CurrentUser
import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.PageResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

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
    fun createChat(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Valid @RequestBody request: CreateChatRequest,
    ): ResponseEntity<CreateChatResponse> {
        if (request.streaming) {
            throw BadRequestException("streaming requests must use Accept: text/event-stream")
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.createChat(currentUser.id, request))
    }

    @PostMapping(
        headers = [HttpHeaders.ACCEPT + "=" + MediaType.TEXT_EVENT_STREAM_VALUE],
        produces = [MediaType.TEXT_EVENT_STREAM_VALUE],
    )
    fun createStreamingChat(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @Valid @RequestBody request: CreateChatRequest,
    ): SseEmitter {
        if (!request.streaming) {
            throw BadRequestException("isStreaming must be true")
        }

        return chatService.createStreamingChat(currentUser.id, request)
    }
}
