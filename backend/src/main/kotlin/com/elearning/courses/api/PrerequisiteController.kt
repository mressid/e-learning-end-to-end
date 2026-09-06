package com.elearning.courses.api

import com.elearning.courses.application.PrerequisiteService
import com.elearning.courses.application.PrerequisiteNoteView
import com.elearning.courses.application.PrerequisiteView
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
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(name = "SetCoursePrerequisitesRequest")
data class SetCoursePrerequisitesRequest(
    @get:Schema(
        description = "Free text, in the order a reader should see it. Replaces the " +
            "whole list; an empty list clears it.",
        example = "[\"Basic Python\", \"Comfortable with matrices\"]",
    )
    @field:Size(max = 20)
    val prerequisites: List<@NotBlank @Size(max = 500) String> = emptyList(),
)

@Schema(name = "SetItemPrerequisitesRequest")
data class SetPrerequisitesRequest(
    @get:Schema(description = "Replaces the current set; an empty list clears it")
    @field:Size(max = 20)
    val prerequisiteIds: List<UUID> = emptyList(),
)

@Schema(name = "CoursePrerequisite", description = "A stated requirement, in the author's words")
data class CoursePrerequisiteResponse(val text: String) {
    companion object {
        fun of(n: PrerequisiteNoteView) = CoursePrerequisiteResponse(n.text)
    }
}

@Schema(name = "PrerequisiteResponse")
data class PrerequisiteResponse(val id: UUID, val title: String) {
    companion object {
        fun of(p: PrerequisiteView) = PrerequisiteResponse(p.id, p.title)
    }
}

/**
 * What has to be finished before something else may be started.
 *
 * Reads are public for courses, because "what do I need before this?" is part
 * of deciding whether to sign up. Writes are for course editors.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Prerequisites", description = "Ordering requirements between courses and between items")
class PrerequisiteController(
    private val service: PrerequisiteService,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/courses/{courseId}/prerequisites")
    @Operation(
        summary = "What a course expects you to know already",
        description = "Free text written by the author. Nothing enforces it - " +
            "enrolment is not blocked by these.",
    )
    fun coursePrerequisites(@PathVariable courseId: UUID): List<CoursePrerequisiteResponse> =
        service.listCoursePrerequisites(courseId, currentUser.idOrNull())
            .map(CoursePrerequisiteResponse::of)

    @PutMapping("/courses/{courseId}/prerequisites")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Set what a course expects you to know already",
        description = "Replaces the whole list. Blank entries and duplicates are " +
            "dropped rather than rendered as empty or repeated bullets.",
    )
    fun setCoursePrerequisites(
        @PathVariable courseId: UUID,
        @Valid @RequestBody request: SetCoursePrerequisitesRequest,
    ): List<CoursePrerequisiteResponse> =
        service.setCoursePrerequisites(courseId, request.prerequisites, currentUser.requireId())
            .map(CoursePrerequisiteResponse::of)

    @GetMapping("/items/{itemId}/prerequisites")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Items that must be completed before this one")
    fun itemPrerequisites(@PathVariable itemId: UUID): List<PrerequisiteResponse> =
        service.listItemPrerequisites(itemId, currentUser.idOrNull()).map(PrerequisiteResponse::of)

    @PutMapping("/items/{itemId}/prerequisites")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Set an item's prerequisites",
        description = "Replaces the whole set. Prerequisites must belong to the " +
            "same course, or the item could never be unlocked.",
    )
    fun setItemPrerequisites(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: SetPrerequisitesRequest,
    ): List<PrerequisiteResponse> =
        service.setItemPrerequisites(itemId, request.prerequisiteIds, currentUser.requireId())
            .map(PrerequisiteResponse::of)
}
