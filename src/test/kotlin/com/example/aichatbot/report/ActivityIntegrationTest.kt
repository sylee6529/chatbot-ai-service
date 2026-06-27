package com.example.aichatbot.report

import com.example.aichatbot.common.ForbiddenException
import com.example.aichatbot.user.Role
import com.example.aichatbot.user.User
import com.example.aichatbot.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@SpringBootTest
@Transactional
class ActivityIntegrationTest(
    @Autowired private val reportService: ReportService,
    @Autowired private val activityLogRepository: ActivityLogRepository,
    @Autowired private val userRepository: UserRepository,
    @Autowired private val passwordEncoder: PasswordEncoder,
    @Autowired private val clock: Clock,
) {

    @Test
    fun `admin gets recent twenty four hour activity counts`() {
        val user = createUser("activity-user", Role.MEMBER)
        val now = Instant.now(clock)
        val before = reportService.getActivity(Role.ADMIN)
        activityLogRepository.save(ActivityLog(user = user, type = ActivityType.SIGNUP, createdAt = now.minusSeconds(60)))
        activityLogRepository.save(ActivityLog(user = user, type = ActivityType.LOGIN, createdAt = now.minusSeconds(30)))
        activityLogRepository.save(ActivityLog(user = user, type = ActivityType.CHAT_CREATED, createdAt = now.minusSeconds(10)))
        activityLogRepository.save(ActivityLog(user = user, type = ActivityType.CHAT_CREATED, createdAt = now.minusSeconds(90_000)))

        val response = reportService.getActivity(Role.ADMIN)

        assertEquals(before.signups + 1, response.signups)
        assertEquals(before.logins + 1, response.logins)
        assertEquals(before.chatsCreated + 1, response.chatsCreated)
    }

    @Test
    fun `member cannot get activity`() {
        assertFailsWith<ForbiddenException> {
            reportService.getActivity(Role.MEMBER)
        }
    }

    private fun createUser(prefix: String, role: Role): User =
        userRepository.save(
            User(
                email = "$prefix-${UUID.randomUUID()}@example.com",
                passwordHash = passwordEncoder.encode("Pw123456!"),
                name = "Tester",
                role = role,
                createdAt = Instant.now(clock),
            ),
        )
}
