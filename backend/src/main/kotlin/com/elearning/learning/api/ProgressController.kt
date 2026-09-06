package com.elearning.learning.api

import com.elearning.learning.application.ProgressService
import com.elearning.learning.application.RecordProgressCommand
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "Learning", description = "Enrolments and course progress")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class ProgressController(
    private val progressService: ProgressService,
    private val currentUser: CurrentUser,
) {

    @PostMapping("/{itemId}/progress")
    @Operation(
        summary = "Record progress on a course item",
        description = "Progress only moves forward. Completing the last required item completes the course.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Recorded"),
        ApiResponse(
            responseCode = "403",
            description = "Not enrolled, or the enrolment is no longer active",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun record(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: RecordProgressRequest,
    ): ProgressResponse = ProgressResponse.of(
        progressService.record(
            itemId,
            currentUser.requireId(),
            RecordProgressCommand(
                status = request.status,
                progressPercent = request.progressPercent,
                lastPositionSeconds = request.lastPositionSeconds,
            ),
        ),
    )
}
