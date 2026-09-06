package com.elearning.courses.api

import com.elearning.courses.application.CourseService
import com.elearning.courses.application.UserDirectory
import com.elearning.courses.domain.Course
import com.elearning.courses.domain.CourseStatus
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.security.PlatformAccess
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "AdminCourseResponse", description = "A course as the dashboard lists it")
data class AdminCourseResponse(
    val id: UUID,
    val title: String,
    val slug: String,
    val status: String,
    val level: String,
    val language: String,
    val ownerId: UUID,
    val ownerName: String?,
    val ownerEmail: String?,
    val createdAt: Instant,
    val publishedAt: Instant?,
)

/**
 * The dashboard's view of the catalogue: every course, whatever its status.
 *
 * A separate endpoint from `/api/v1/courses` rather than a flag on it. That one
 * lists PUBLISHED only, and that filter is the single thing keeping drafts off
 * the public listing - making it conditional would put the decision one boolean
 * away from being wrong on the busiest endpoint on the platform.
 */
@RestController
@RequestMapping("/api/v1/admin/courses")
@Tag(name = "Admin: courses", description = "The whole catalogue, drafts included")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminCourseController(
    private val courseService: CourseService,
    private val directory: UserDirectory,
    private val platformAccess: PlatformAccess,
) {

    @GetMapping
    @Operation(
        summary = "List every course",
        description = "Requires `course.read`. Filter with `q` (title substring), " +
            "`status`, or `owner`; `q` wins, then `status`, then `owner`.",
    )
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) owner: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminCourseResponse> {
        val parsed = status?.let {
            runCatching { CourseStatus.valueOf(it.uppercase()) }
                .getOrElse { throw BusinessRuleException("INVALID_STATUS", "Unknown status $status") }
        }

        val results = courseService.listForAdministration(
            q,
            parsed,
            owner,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        // One lookup for the page: an owner name per row would be the N+1 this
        // codebase avoids everywhere else it renders a list.
        val owners = directory.summaries(results.content.map { it.ownerId })
        return PageResponse.from(results) { toResponse(it, owners) }
    }

    @GetMapping("/stats")
    @Operation(
        summary = "Counts by status, for the dashboard's overview tiles",
        description = "Requires `course.read`.",
    )
    fun stats(): Map<String, Long> {
        platformAccess.require("course.read")
        return courseService.countsByStatus().mapKeys { it.key.name }
    }

    private fun toResponse(
        course: Course,
        owners: Map<UUID, com.elearning.courses.application.UserSummary>,
    ): AdminCourseResponse {
        val owner = owners[course.ownerId]
        return AdminCourseResponse(
            id = requireNotNull(course.id),
            title = course.title,
            slug = course.slug,
            status = course.status.name,
            level = course.level.name,
            language = course.language,
            ownerId = course.ownerId,
            ownerName = owner?.displayName ?: owner?.username,
            ownerEmail = owner?.email,
            createdAt = course.createdAt,
            publishedAt = course.publishedAt,
        )
    }
}
