package com.elearning.courses.api

import com.elearning.courses.application.CourseStructureService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.ReorderRequest
import com.elearning.shared.security.CurrentActor
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Sections are addressed directly once created: nesting item URLs under the
 * course as well would make the same item reachable by two paths.
 */
@RestController
@RequestMapping("/api/v1/sections")
@Tag(name = "Course sections", description = "Items within a section")
class SectionController(
    private val structureService: CourseStructureService,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @GetMapping("/{sectionId}/items")
    @Operation(summary = "List a section's items, in order")
    fun items(@PathVariable sectionId: UUID): List<CourseItemResponse> =
        structureService.listItems(sectionId, currentUser.idOrNull()).map(CourseItemResponse::of)

    @PutMapping("/{sectionId}/items/order")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Reorder a section's items",
        description = "Send every item id in the order you want. Partial orders are rejected.",
    )
    fun reorderItems(
        @PathVariable sectionId: UUID,
        @Valid @RequestBody request: ReorderRequest,
    ): List<CourseItemResponse> =
        structureService.reorderItems(sectionId, request.orderedIds, currentUser.requireId())
            .map(CourseItemResponse::of)

    @PatchMapping("/{sectionId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Rename a section, or rewrite its description",
        description = "Only the fields you send change. Use the reorder endpoint to move it: " +
            "position is a property of the whole sequence, not of one section.",
    )
    fun updateSection(
        @PathVariable sectionId: UUID,
        @Valid @RequestBody request: UpdateSectionRequest,
    ): SectionResponse = SectionResponse.of(
        structureService.updateSection(
            sectionId,
            request.title,
            request.description,
            currentUser.requireId(),
        ),
    )

    @DeleteMapping("/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Delete a section and its items",
        description = "Refused with 422 if any item under it has student progress, " +
            "quiz attempts or assignment submissions - deleting would destroy them. " +
            "All or nothing, so a section is never left half-emptied.",
    )
    fun deleteSection(@PathVariable sectionId: UUID) =
        structureService.deleteSection(sectionId, currentActor.requireId())

    @PostMapping("/{sectionId}/items")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Append an item to a section",
        description = "Lessons, quizzes and assignments share one ordered sequence.",
    )
    fun addItem(
        @PathVariable sectionId: UUID,
        @Valid @RequestBody request: CreateCourseItemRequest,
    ): ResponseEntity<CourseItemResponse> {
        val item = structureService.addItem(
            sectionId = sectionId,
            title = request.title,
            type = request.type,
            isRequired = request.isRequired,
            editorId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseItemResponse.of(item))
    }
}
