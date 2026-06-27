package com.example.aichatbot.chat

import com.example.aichatbot.auth.CurrentUser
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/threads")
class ThreadController(
    private val threadService: ThreadService,
) {

    @DeleteMapping("/{threadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteThread(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @PathVariable threadId: Long,
    ) {
        threadService.deleteThread(currentUser.id, currentUser.role, threadId)
    }
}
