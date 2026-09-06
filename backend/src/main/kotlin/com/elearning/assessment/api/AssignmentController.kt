package com.elearning.assessment.api

import com.elearning.assessment.application.AssignmentService
import com.elearning.assessment.application.GradeSubmissionCommand
import com.elearning.assessment.application.SaveAssignmentCommand
import com.elearning.assessment.application.SubmitAssignmentCommand
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.ApiError
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Assignments", description = "Assignment authoring, submission and grading")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AssignmentController(
    private val assignmentService: AssignmentService,
    private val currentUser: CurrentUser,
) {

    @PutMapping("/items/{itemId}/assignment")
    @Operation(summary = "Create or replace the assignment on an ASSIGNMENT course item")
    fun save(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: SaveAssignmentRequest,
    ): AssignmentResponse = AssignmentResponse.of(
        assignmentService.upsert(
            itemId,
            SaveAssignmentCommand(
                instructions = request.instructions,
                maxScore = request.maxScore,
                dueAt = request.dueAt,
                allowLateSubmission = request.allowLateSubmission,
            ),
            editorId = currentUser.requireId(),
        ),
    )

    @GetMapping("/items/{itemId}/assignment")
    @Operation(summary = "Read the assignment brief")
    fun get(@PathVariable itemId: UUID): AssignmentResponse =
        AssignmentResponse.of(assignmentService.get(itemId, currentUser.requireId()))

    @PostMapping("/items/{itemId}/assignment/submissions")
    @Operation(
        summary = "Hand in work",
        description = "Late work is refused unless the assignment allows it. Files must be your own completed uploads.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Submitted"),
        ApiResponse(
            responseCode = "422",
            description = "Past the due date, or nothing was submitted",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun submit(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: SubmitAssignmentRequest,
    ): ResponseEntity<SubmissionResponse> {
        val submission = assignmentService.submit(
            itemId,
            SubmitAssignmentCommand(content = request.content, mediaIds = request.mediaIds),
            studentId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            SubmissionResponse.of(submission, assignmentService.mediaIdsOf(requireNotNull(submission.id))),
        )
    }

    @GetMapping("/items/{itemId}/assignment/submissions/me")
    @Operation(summary = "Your own submissions for this assignment")
    fun mySubmissions(@PathVariable itemId: UUID): List<SubmissionResponse> =
        assignmentService.mySubmissions(itemId, currentUser.requireId())
            .map { SubmissionResponse.of(it, assignmentService.mediaIdsOf(requireNotNull(it.id))) }

    @GetMapping("/items/{itemId}/assignment/submissions")
    @Operation(summary = "All submissions for grading", description = "Course editors only.")
    fun submissions(
        @PathVariable itemId: UUID,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<SubmissionResponse> = PageResponse.from(
        assignmentService.submissionsForEditor(itemId, currentUser.requireId(), PageRequest.of(page, size)),
    ) { SubmissionResponse.of(it, assignmentService.mediaIdsOf(requireNotNull(it.id))) }

    @PostMapping("/submissions/{submissionId}/grade")
    @Operation(summary = "Grade a submission", description = "Completes the course item for that student.")
    fun grade(
        @PathVariable submissionId: UUID,
        @Valid @RequestBody request: GradeSubmissionRequest,
    ): SubmissionResponse {
        val graded = assignmentService.grade(
            submissionId,
            GradeSubmissionCommand(score = request.score, feedback = request.feedback),
            editorId = currentUser.requireId(),
        )
        return SubmissionResponse.of(graded, assignmentService.mediaIdsOf(requireNotNull(graded.id)))
    }
}
