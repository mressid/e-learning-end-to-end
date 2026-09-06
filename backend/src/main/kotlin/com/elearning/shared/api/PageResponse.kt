package com.elearning.shared.api

import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.data.domain.Page

/**
 * The one pagination envelope for every collection endpoint (§28).
 *
 * Spring Data's `Page` is not returned directly: its JSON shape is an
 * unstable implementation detail, and exposing it would make the persistence
 * library part of the public API contract.
 */
@Schema(name = "PageResponse")
data class PageResponse<T>(
    val content: List<T>,
    @get:Schema(description = "Zero-based page index") val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
) {
    companion object {
        fun <S : Any, T> from(page: Page<S>, transform: (S) -> T) = PageResponse(
            content = page.content.map(transform),
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            first = page.isFirst,
            last = page.isLast,
        )
    }
}
