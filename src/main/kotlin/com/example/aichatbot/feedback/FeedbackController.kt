package com.example.aichatbot.feedback

import com.example.aichatbot.auth.CurrentUser
import com.example.aichatbot.common.PageResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class FeedbackController(
    private val feedbackService: FeedbackService,
) {

    @PostMapping("/api/v1/chats/{chatId}/feedbacks")
    @ResponseStatus(HttpStatus.CREATED)
    fun createFeedback(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @PathVariable chatId: Long,
        @RequestBody request: CreateFeedbackRequest,
    ): FeedbackResponse =
        feedbackService.createFeedback(currentUser.id, currentUser.role, chatId, request)

    @GetMapping("/api/v1/feedbacks")
    fun getFeedbacks(
        @AuthenticationPrincipal currentUser: CurrentUser,
        isPositive: Boolean? = null,
        status: String? = null,
        page: Int = 0,
        size: Int = 20,
        sort: String? = null,
    ): PageResponse<FeedbackResponse> =
        feedbackService.getFeedbacks(currentUser.id, currentUser.role, isPositive, status, page, size, sort)

    @PatchMapping("/api/v1/feedbacks/{id}/status")
    fun updateStatus(
        @AuthenticationPrincipal currentUser: CurrentUser,
        @PathVariable id: Long,
        @RequestBody request: UpdateFeedbackStatusRequest,
    ): UpdateFeedbackStatusResponse =
        feedbackService.updateStatus(currentUser.role, id, request)
}
