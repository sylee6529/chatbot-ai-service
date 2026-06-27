package com.example.aichatbot.common

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AppBeans {
    @Bean
    fun clock(): Clock = Clock.systemUTC()
}
