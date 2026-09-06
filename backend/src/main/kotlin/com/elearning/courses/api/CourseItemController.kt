package com.elearning.courses.api

import com.elearning.courses.application.CourseStructureService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.errors.ApiError
import com.elearning.shared.security.CurrentActor
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Operations on an item itself.
 *
 * Items live at `/api/v1/items/{id}` throughout - that is where their lesson,
 * quiz, assignment, progress and prerequisites already hang - so removing one
 * belongs here rather than under the section that happens to contain it.
 */
@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "Course items", description = "Individual items of a course")
class CourseItemController(
    private val structureService: CourseStructureService,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @DeleteMapping("/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Delete a course item",
        description = "Permanent. Refused once a student has touched it: the delete " +
            "cascades into learning progress, quiz attempts and assignment " +
            "submissions, and none of that comes back. Archive the course instead.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Deleted"),
        ApiResponse(
            responseCode = "422",
            description = "A student has worked on this item",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun delete(@PathVariable itemId: UUID) =
        structureService.deleteItem(itemId, currentActor.requireId())
}
