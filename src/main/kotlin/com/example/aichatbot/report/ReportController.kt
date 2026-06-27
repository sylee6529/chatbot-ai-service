package com.example.aichatbot.report

import com.example.aichatbot.auth.CurrentUser
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
class ReportController(
    private val reportService: ReportService,
) {

    @GetMapping("/activity")
    fun getActivity(@AuthenticationPrincipal currentUser: CurrentUser): ActivityResponse =
        reportService.getActivity(currentUser.role)

    @GetMapping("/reports/chats.csv")
    fun getChatsCsv(@AuthenticationPrincipal currentUser: CurrentUser): ResponseEntity<String> =
        ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"chats_last_24h.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(reportService.getChatsCsv(currentUser.role))
}
