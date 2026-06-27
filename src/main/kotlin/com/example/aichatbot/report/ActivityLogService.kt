package com.example.aichatbot.report

import com.example.aichatbot.user.User
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant

@Service
class ActivityLogService(
    private val activityLogRepository: ActivityLogRepository,
    private val clock: Clock,
) {
    fun record(user: User?, type: ActivityType) {
        activityLogRepository.save(
            ActivityLog(
                user = user,
                type = type,
                createdAt = Instant.now(clock),
            ),
        )
    }
}
