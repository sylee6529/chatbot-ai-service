package com.example.aichatbot.report

import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.user.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

@Service
class ReportService(
    private val activityLogRepository: ActivityLogRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun getActivity(role: Role): ActivityResponse {
        requireAdmin(role)
        val end = Instant.now(clock)
        val start = end.minus(Duration.ofHours(24))
        return ActivityResponse(
            windowStart = start,
            windowEnd = end,
            signups = activityLogRepository.countByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ActivityType.SIGNUP, start, end),
            logins = activityLogRepository.countByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ActivityType.LOGIN, start, end),
            chatsCreated = activityLogRepository.countByTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ActivityType.CHAT_CREATED, start, end),
        )
    }

    @Transactional(readOnly = true)
    fun getChatsCsv(role: Role): String {
        requireAdmin(role)
        val end = Instant.now(clock)
        val start = end.minus(Duration.ofHours(24))
        val rows = activityLogRepository.findChatReportRows(start, end)
        return buildString {
            appendLine("chat_id,thread_id,user_id,user_email,user_name,model,question,answer,chat_created_at")
            rows.forEach {
                appendLine(
                    listOf(
                        it.chatId.toString(),
                        it.threadId.toString(),
                        it.userId.toString(),
                        csv(it.userEmail),
                        csv(it.userName),
                        csv(it.model.orEmpty()),
                        csv(it.question),
                        csv(it.answer),
                        it.chatCreatedAt.toString(),
                    ).joinToString(","),
                )
            }
        }
    }

    private fun requireAdmin(role: Role) {
        if (role != Role.ADMIN) {
            throw ForbiddenException()
        }
    }

    private fun csv(value: String): String =
        "\"${value.replace("\"", "\"\"")}\""
}
