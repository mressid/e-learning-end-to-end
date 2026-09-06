package com.elearning.learning.api

import com.elearning.learning.application.LessonService
import com.elearning.learning.application.SaveLessonCommand
import com.elearning.platform.media.StorageProperties
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/items/{itemId}/lesson")
@Tag(name = "Lessons", description = "Content behind a LESSON course item")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class LessonController(
    private val lessonService: LessonService,
    private val currentUser: CurrentUser,
    private val storageProperties: StorageProperties,
) {

    @PutMapping
    @Operation(
        summary = "Create or replace a lesson's content",
        description = "Course editors only. Idempotent: the item's id is the lesson's id.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Saved"),
        ApiResponse(
            responseCode = "403",
            description = "Not an editor of this course, or the media is not yours",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Content missing for the chosen type, or the upload is not complete",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun save(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: SaveLessonRequest,
    ): LessonResponse = LessonResponse.of(
        lessonService.upsert(
            itemId,
            SaveLessonCommand(
                contentType = request.contentType,
                description = request.description,
                durationSeconds = request.durationSeconds,
                completionRule = request.completionRule,
                content = request.content,
                mediaId = request.mediaId,
                thumbnailMediaId = request.thumbnailMediaId,
            ),
            editorId = currentUser.requireId(),
        ),
    )

    @GetMapping
    @Operation(
        summary = "Read a lesson",
        description = "Course editors, or students with an active enrolment.",
    )
    fun get(@PathVariable itemId: UUID): LessonResponse =
        LessonResponse.of(lessonService.get(itemId, currentUser.requireId()))

    @GetMapping("/content-url")
    @Operation(
        summary = "Short-lived URL for the lesson's file",
        description = "Media ids are never exposed; access follows from the enrolment.",
    )
    fun contentUrl(@PathVariable itemId: UUID): LessonContentUrlResponse =
        LessonContentUrlResponse(
            contentUrl = lessonService.contentUrl(itemId, currentUser.requireId()).toString(),
            expiresInSeconds = storageProperties.presignedUrlTtl.seconds,
        )
}
