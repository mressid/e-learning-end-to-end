package com.elearning.learning.api

import com.elearning.learning.application.EnrollmentService
import com.elearning.learning.application.ProgressService
import com.elearning.learning.domain.EnrollmentStatus
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
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Learning", description = "Enrolments and course progress")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class EnrollmentController(
    private val enrollmentService: EnrollmentService,
    private val progressService: ProgressService,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/courses/{courseId}/enroll")
    @Operation(summary = "Enrol the caller in a published course")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Enrolled"),
        ApiResponse(
            responseCode = "409",
            description = "Already enrolled",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "The course is not published",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun enroll(@PathVariable courseId: UUID): ResponseEntity<EnrollmentResponse> {
        val enrollment = enrollmentService.enroll(courseId, currentUser.requireId())
        return ResponseEntity.status(HttpStatus.CREATED).body(EnrollmentResponse.of(enrollment))
    }

    @GetMapping("/me/enrollments")
    @Operation(summary = "List the caller's enrolments")
    fun myEnrollments(
        @RequestParam(required = false) status: EnrollmentStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<EnrollmentResponse> = PageResponse.from(
        enrollmentService.listForStudent(
            currentUser.requireId(),
            status,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt")),
        ),
        EnrollmentResponse::of,
    )

    @PostMapping("/enrollments/{enrollmentId}/cancel")
    @Operation(summary = "Cancel one of the caller's enrolments")
    fun cancel(@PathVariable enrollmentId: UUID): EnrollmentResponse =
        EnrollmentResponse.of(enrollmentService.cancel(enrollmentId, currentUser.requireId()))

    @GetMapping("/courses/{courseId}/progress")
    @Operation(
        summary = "The caller's progress through a course",
        description = "Percentage is over required items only.",
    )
    fun courseProgress(@PathVariable courseId: UUID): CourseProgressResponse =
        CourseProgressResponse.of(progressService.courseProgress(courseId, currentUser.requireId()))
}
