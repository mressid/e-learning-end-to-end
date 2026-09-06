package com.elearning.courses.api

import com.elearning.courses.application.CourseInstructorService
import com.elearning.courses.domain.CourseInstructor
import com.elearning.courses.domain.InstructorRole
import com.elearning.shared.api.OpenApiConfig
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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(name = "AddInstructorRequest")
data class AddInstructorRequest(
    val instructorId: UUID,
    val role: InstructorRole = InstructorRole.ASSISTANT,
)

@Schema(name = "InstructorResponse")
data class InstructorResponse(val instructorId: UUID, val role: String) {
    companion object {
        fun of(i: CourseInstructor) = InstructorResponse(i.id.instructorId, i.role.name)
    }
}

@RestController
@RequestMapping("/api/v1/courses/{courseId}/instructors")
@Tag(name = "Course instructors", description = "Co-teachers on a course")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class CourseInstructorController(
    private val service: CourseInstructorService,
    private val currentUser: CurrentUser,
) {

    @PostMapping
    @Operation(
        summary = "Add a co-instructor",
        description = "Owner only. Idempotent — adding the same person again updates their role.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Added"),
        ApiResponse(
            responseCode = "403",
            description = "Only the owner may manage instructors",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun add(
        @PathVariable courseId: UUID,
        @Valid @RequestBody request: AddInstructorRequest,
    ): ResponseEntity<InstructorResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        InstructorResponse.of(
            service.add(courseId, request.instructorId, request.role, currentUser.requireId()),
        ),
    )

    @GetMapping
    @Operation(summary = "List co-instructors", description = "Any course editor.")
    fun list(@PathVariable courseId: UUID): List<InstructorResponse> =
        service.list(courseId, currentUser.requireId()).map(InstructorResponse::of)

    @DeleteMapping("/{instructorId}")
    @Operation(summary = "Remove a co-instructor", description = "Owner only.")
    fun remove(
        @PathVariable courseId: UUID,
        @PathVariable instructorId: UUID,
    ): ResponseEntity<Unit> {
        service.remove(courseId, instructorId, currentUser.requireId())
        return ResponseEntity.noContent().build()
    }
}
