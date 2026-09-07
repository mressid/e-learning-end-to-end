package com.elearning.courses.api

import com.elearning.courses.application.UserDirectory
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
 * Listed from the `users.is_instructor` flag added in V11, not derived from
 * course ownership as it was before. The derivation read well but could not show
 * someone an administrator had just enrolled and who had not started a course
 * yet - which is precisely the person you look for after adding them.
 *
 * The flag is *not* authority over any particular course. That is still
 * ownership or co-instructorship (§11); this only says who may start one, which
 * no relationship can express because the course does not exist yet.
 *
 * Still served from `courses`, because the counts are its data and the
 * `UserDirectory` port keeps the module dependency one-directional (§8).
 * Creating and editing these accounts lives in `identity`, under
 * `/api/v1/admin/users`, since that is the module that owns the row.
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
        summary = "List instructors",
        description = "Requires `user.read`. Everyone flagged as an instructor, including " +
            "those with no courses yet. Pass `q` to match an email or username. " +
            "Create and edit them through `/api/v1/admin/users`.",
    )
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<InstructorRosterEntry> {
        platformAccess.require("user.read")

        val people = directory.instructors(q, PageRequest.of(page, size))
        // Counts for the whole page in one grouped query. Someone with no
        // courses simply has no row here, and reads as zero.
        val counts = if (people.isEmpty) {
            emptyMap()
        } else {
            courses.countsForOwners(people.content.map { it.id }).associateBy { it.instructorId }
        }

        return PageResponse.from(people) { person ->
            val row = counts[person.id]
            InstructorRosterEntry(
                id = person.id,
                email = person.email,
                username = person.username,
                displayName = person.displayName,
                courseCount = row?.courseCount ?: 0,
                publishedCourseCount = row?.publishedCount ?: 0,
            )
        }
    }
}
