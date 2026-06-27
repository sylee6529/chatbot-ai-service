package com.example.aichatbot.chat

import org.springframework.data.jpa.repository.JpaRepository

interface ChatThreadRepository : JpaRepository<ChatThread, Long>
