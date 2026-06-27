package com.example.aichatbot.common

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val sort: String,
) {
    companion object {
        fun <T> of(page: Page<*>, content: List<T>, sort: String): PageResponse<T> =
            PageResponse(
                content = content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                sort = sort,
            )
    }
}
