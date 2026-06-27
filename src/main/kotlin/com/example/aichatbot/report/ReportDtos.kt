package com.example.aichatbot.report

import java.time.Instant

data class ActivityResponse(
    val windowStart: Instant,
    val windowEnd: Instant,
    val signups: Long,
    val logins: Long,
    val chatsCreated: Long,
)
