package com.elearning.assessment.api

import com.elearning.assessment.application.LearningContext
import com.elearning.assessment.domain.AssignmentSubmission
import com.elearning.assessment.domain.SubmissionStatus
import com.elearning.assessment.infrastructure.AssignmentSubmissionRepository
import com.elearning.identity.application.UserLookupService
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
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(name = "AdminSubmissionResponse")
data class AdminSubmissionResponse(
    val id: UUID,
    val status: String,
    val studentId: UUID,
    val studentName: String?,
    val studentEmail: String?,
    val courseItemId: UUID,
    val assignmentTitle: String?,
    val courseId: UUID?,
    val courseTitle: String?,
    val attemptNumber: Int,
    val score: BigDecimal?,
    val submittedAt: Instant?,
    val gradedAt: Instant?,
)

/**
 * The grading queue across every course, for the dashboard's `/submissions`.
 *
 * **Read-only, deliberately.** `assignment_submissions.graded_by` references
 * `users`, and an administrator is not a row in that table - there is nowhere to
 * record them as the grader. Marking therefore stays with the course's own
 * instructors, and this page exists to find work that is waiting rather than to
 * do it. Letting admins grade needs a second nullable column, which is a schema
 * decision to take deliberately rather than imply from a listing.
 *
 * The submitted **content is not returned** either: the queue answers "what is
 * outstanding, and whose", and a student's actual answers are for whoever marks
 * them.
 */
@RestController
@RequestMapping("/api/v1/admin/submissions")
@Tag(name = "Admin: submissions", description = "The grading queue")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminSubmissionController(
    private val submissions: AssignmentSubmissionRepository,
    private val context: LearningContext,
    private val people: UserLookupService,
    private val platformAccess: PlatformAccess,
) {

    @GetMapping
    @Operation(
        summary = "List submissions, newest first",
        description = "Requires `submission.read`. Filter with `status` - " +
            "`SUBMITTED` is the queue of work waiting to be marked. Grading is " +
            "not available here; it belongs to the course's instructors.",
    )
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminSubmissionResponse> {
        platformAccess.require("submission.read")

        val parsed = status?.let {
            runCatching { SubmissionStatus.valueOf(it.uppercase()) }
                .getOrElse { throw BusinessRuleException("INVALID_STATUS", "Unknown status $status") }
        }
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"))
        val results = if (parsed != null) {
            submissions.findByStatus(parsed, pageable)
        } else {
            submissions.findAll(pageable)
        }
        return PageResponse.from(results, decorate(results.content))
    }

    /** One lookup per kind for the page, rather than three per row. */
    private fun decorate(rows: List<AssignmentSubmission>): (AssignmentSubmission) -> AdminSubmissionResponse {
        val authors = people.summaries(rows.map { it.studentId })
        val itemIds = rows.map { it.assignmentId }.distinct()
        // An assignment's primary key *is* its course item id, so the item
        // lookups take the assignment ids directly.
        val itemTitles = itemIds.associateWith { context.itemTitle(it) }
        val courseIds = itemIds.associateWith { context.courseIdOfItem(it) }
        val courseTitles = courseIds.values.filterNotNull().distinct()
            .associateWith { context.courseTitle(it) }

        return { s ->
            val author = authors[s.studentId]
            val courseId = courseIds[s.assignmentId]
            AdminSubmissionResponse(
                id = requireNotNull(s.id),
                status = s.status.name,
                studentId = s.studentId,
                studentName = author?.label,
                studentEmail = author?.email,
                courseItemId = s.assignmentId,
                assignmentTitle = itemTitles[s.assignmentId],
                courseId = courseId,
                courseTitle = courseId?.let { courseTitles[it] },
                attemptNumber = s.attemptNumber,
                score = s.score,
                submittedAt = s.submittedAt,
                gradedAt = s.gradedAt,
            )
        }
    }
}
