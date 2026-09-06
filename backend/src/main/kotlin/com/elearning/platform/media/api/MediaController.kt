package com.elearning.platform.media.api

import com.elearning.platform.media.MediaService
import com.elearning.platform.media.RequestUploadCommand
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
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/media")
@Tag(
    name = "Media",
    description = "Direct-to-storage uploads. Bytes never pass through this API.",
)
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class MediaController(
    private val mediaService: MediaService,
    private val currentUser: CurrentUser,
    private val storageProperties: StorageProperties,
) {

    @PostMapping("/uploads")
    @Operation(
        summary = "Request an upload URL",
        description = """
            Returns a short-lived presigned PUT URL. Upload the file straight to
            it with the same Content-Type, then call `/media/{id}/complete`.
        """,
    )
    fun requestUpload(
        @Valid @RequestBody request: RequestUploadRequest,
    ): ResponseEntity<UploadTicketResponse> {
        val ticket = mediaService.requestUpload(
            RequestUploadCommand(
                filename = request.filename,
                contentType = request.contentType,
                visibility = request.visibility,
            ),
            uploaderId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadTicketResponse.of(ticket))
    }

    @PostMapping("/{mediaId}/complete")
    @Operation(
        summary = "Confirm an upload finished",
        description = "The size is read back from storage, not taken from the client.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Now AVAILABLE"),
        ApiResponse(
            responseCode = "422",
            description = "No object was actually uploaded",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Not your media object",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun complete(@PathVariable mediaId: UUID): MediaObjectResponse =
        MediaObjectResponse.of(mediaService.completeUpload(mediaId, currentUser.requireId()))

    @GetMapping("/{mediaId}/download-url")
    @Operation(summary = "Get a short-lived download URL")
    fun downloadUrl(@PathVariable mediaId: UUID): DownloadUrlResponse =
        DownloadUrlResponse(
            downloadUrl = mediaService.downloadUrl(mediaId, currentUser.requireId()).toString(),
            expiresInSeconds = storageProperties.presignedUrlTtl.seconds,
        )
}
