package com.elearning.courses.api

import com.elearning.courses.application.UserDirectory
import com.elearning.courses.domain.CourseStatus
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.security.PlatformAccess
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(name = "InstructorRosterEntry")
data class InstructorRosterEntry(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String?,
    val courseCount: Long,
    val publishedCourseCount: Long,
)

/**
 * The instructor roster, for the dashboard's `/instructors` page.
 *
 * Derived from **course ownership**, not from a role: instructor-ness on this
 * platform is a relationship, and there is no column that says otherwise (§11).
 * Served from `courses` because that is the module owning the relationship;
 * identity is reached through the `UserDirectory` port for names only.
 */
@RestController
@RequestMapping("/api/v1/admin/instructors")
@Tag(name = "Admin: instructors", description = "People who own courses")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminInstructorController(
    private val courses: CourseRepository,
    private val directory: UserDirectory,
    private val platformAccess: PlatformAccess,
) {

    @GetMapping
    @Operation(
        summary = "List instructors, busiest first",
        description = "Requires `user.read`. An instructor is someone who owns at least " +
            "one course - there is no instructor role to enumerate.",
    )
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<InstructorRosterEntry> {
        platformAccess.require("user.read")

        val rows = courses.findOwners(PageRequest.of(page, size))
        // One lookup for the whole page rather than a name query per row.
        val people = directory.summaries(rows.content.map { it.instructorId })

        return PageResponse.from(rows) { row ->
            val person = people[row.instructorId]
            InstructorRosterEntry(
                id = row.instructorId,
                email = person?.email ?: "",
                username = person?.username ?: "",
                displayName = person?.displayName,
                courseCount = row.courseCount,
                publishedCourseCount = courses.countByOwnerAndStatus(
                    row.instructorId,
                    CourseStatus.PUBLISHED,
                ),
            )
        }
    }
}
