package com.example.aichatbot.feedback

import com.example.aichatbot.chat.ChatRepository
import com.example.aichatbot.common.BadRequestException
import com.example.aichatbot.common.ConflictException
import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.common.NotFoundException
import com.example.aichatbot.common.PageResponse
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.UserRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository,
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val clock: Clock,
) {

    @Transactional
    fun createFeedback(
        currentUserId: Long,
        currentUserRole: Role,
        chatId: Long,
        request: CreateFeedbackRequest,
    ): FeedbackResponse {
        val isPositive = request.isPositive ?: throw BadRequestException("isPositive must not be null")
        val user = userRepository.findByIdAndDeletedAtIsNull(currentUserId)
            .orElseThrow { NotFoundException("User not found") }
        val chat = chatRepository.findActiveById(chatId)
            .orElseThrow { NotFoundException("Chat not found") }

        if (currentUserRole != Role.ADMIN && chat.user.id != currentUserId) {
            throw ForbiddenException()
        }
        if (feedbackRepository.existsByUserIdAndChatId(currentUserId, chatId)) {
            throw ConflictException("Feedback already exists for this chat")
        }

        val now = Instant.now(clock)
        val feedback = try {
            feedbackRepository.save(
                Feedback(
                    chat = chat,
                    user = user,
                    isPositive = isPositive,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        } catch (exception: DataIntegrityViolationException) {
            throw ConflictException("Feedback already exists for this chat")
        }
        return feedback.toResponse()
    }

    @Transactional(readOnly = true)
    fun getFeedbacks(
        currentUserId: Long,
        currentUserRole: Role,
        isPositive: Boolean?,
        status: String?,
        page: Int,
        size: Int,
        sort: String?,
    ): PageResponse<FeedbackResponse> {
        if (page < 0) {
            throw BadRequestException("page must be greater than or equal to 0")
        }
        if (size !in 1..100) {
            throw BadRequestException("size must be between 1 and 100")
        }
        val sortSpec = parseFeedbackSort(sort)
        val statusFilter = status?.let { FeedbackStatus.parse(it) }
        val scopedUserId = currentUserId.takeIf { currentUserRole == Role.MEMBER }
        val feedbacks = feedbackRepository.findActiveFeedbacks(
            scopedUserId,
            isPositive,
            statusFilter,
            PageRequest.of(page, size, Sort.by(sortSpec.direction, sortSpec.property)),
        )

        return PageResponse.of(feedbacks, feedbacks.content.map { it.toResponse() }, sortSpec.raw)
    }

    @Transactional
    fun updateStatus(currentUserRole: Role, feedbackId: Long, request: UpdateFeedbackStatusRequest): UpdateFeedbackStatusResponse {
        if (currentUserRole != Role.ADMIN) {
            throw ForbiddenException()
        }
        val status = request.status?.let { FeedbackStatus.parse(it) }
            ?: throw BadRequestException("status must not be blank")
        val feedback = feedbackRepository.findByIdAndDeletedAtIsNull(feedbackId)
            .orElseThrow { NotFoundException("Feedback not found") }
        feedback.updateStatus(status, Instant.now(clock))
        return UpdateFeedbackStatusResponse(
            id = requireNotNull(feedback.id),
            status = feedback.status.value,
            updatedAt = feedback.updatedAt,
        )
    }
}

private data class FeedbackSortSpec(
    val property: String,
    val direction: Sort.Direction,
    val raw: String,
)

private fun parseFeedbackSort(sort: String?): FeedbackSortSpec {
    val raw = sort?.takeIf { it.isNotBlank() } ?: "createdAt,desc"
    val parts = raw.split(",")
    if (parts.size != 2 || parts[0] != "createdAt") {
        throw BadRequestException("sort must be createdAt,asc or createdAt,desc")
    }
    val direction = when (parts[1].lowercase()) {
        "asc" -> Sort.Direction.ASC
        "desc" -> Sort.Direction.DESC
        else -> throw BadRequestException("sort must be createdAt,asc or createdAt,desc")
    }
    return FeedbackSortSpec("createdAt", direction, "createdAt,${parts[1].lowercase()}")
}

private fun Feedback.toResponse(): FeedbackResponse =
    FeedbackResponse(
        id = requireNotNull(id),
        chatId = requireNotNull(chat.id),
        userId = requireNotNull(user.id),
        isPositive = isPositive,
        status = status.value,
        createdAt = createdAt,
    )
