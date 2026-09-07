package com.elearning.platform.media.api

import com.elearning.platform.media.BeginUploadCommand
import com.elearning.platform.media.MediaVisibility
import com.elearning.platform.media.MultipartUploadService
import com.elearning.platform.media.UploadedPart
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
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "BeginMultipartUploadRequest")
data class BeginMultipartUploadRequest(
    @field:Size(max = 255) val filename: String? = null,
    @field:NotBlank val contentType: String,
    @get:Schema(description = "Used to plan the parts; the stored size is read back from storage")
    @field:Positive val sizeBytes: Long,
    val visibility: MediaVisibility = MediaVisibility.PRIVATE,
)

@Schema(name = "MultipartUploadTicket")
data class MultipartTicketResponse(
    val mediaId: UUID,
    val uploadId: String,
    @get:Schema(description = "Bytes per part; every part but the last must be exactly this")
    val partSizeBytes: Long,
    val partCount: Int,
    val createdAt: Instant,
)

@Schema(name = "UploadPartUrl")
data class PartUrlResponse(val partNumber: Int, val url: String, val expiresInSeconds: Long)

@Schema(name = "UploadedPartResponse")
data class UploadedPartResponse(val partNumber: Int, val etag: String, val sizeBytes: Long)

@Schema(name = "CompleteMultipartUploadRequest")
data class CompleteMultipartUploadRequest(val parts: List<PartRef> = emptyList()) {
    @Schema(name = "PartRef")
    data class PartRef(
        @field:Positive val partNumber: Int,
        @get:Schema(description = "Returned by storage in the part's ETag header")
        @field:NotBlank val etag: String,
    )
}

/**
 * Resumable uploads, for files a single PUT cannot carry.
 *
 * The five operations map one-to-one onto what a client-side uploader needs -
 * Uppy's `@uppy/aws-s3` plugin calls exactly these, in this order - so the
 * browser handles chunking, parallelism, retries and progress while the server
 * only signs and records.
 *
 * Bytes never pass through here: parts are presigned exactly as whole objects
 * already are.
 */
@RestController
@RequestMapping("/api/v1/media/uploads/multipart")
@Tag(name = "Media", description = "Direct-to-storage uploads")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class MultipartUploadController(
    private val uploads: MultipartUploadService,
    private val currentUser: CurrentUser,
) {

    @PostMapping
    @Operation(
        summary = "Open a resumable upload",
        description = "Use for anything a single PUT would struggle with. The signed URL " +
            "of an ordinary upload lasts 15 minutes, which a multi-gigabyte file on a " +
            "domestic connection cannot finish inside - and S3 refuses a single PUT " +
            "above 5GB regardless. The part size is chosen from the file size so the " +
            "part count stays inside S3's limit of 10,000.",
    )
    fun begin(
        @Valid @RequestBody request: BeginMultipartUploadRequest,
    ): ResponseEntity<MultipartTicketResponse> {
        val ticket = uploads.begin(
            BeginUploadCommand(
                filename = request.filename,
                contentType = request.contentType,
                sizeBytes = request.sizeBytes,
                visibility = request.visibility,
            ),
            currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            MultipartTicketResponse(
                mediaId = requireNotNull(ticket.media.id),
                uploadId = ticket.uploadId,
                partSizeBytes = ticket.partSizeBytes,
                partCount = ticket.partCount,
                createdAt = ticket.media.createdAt,
            ),
        )
    }

    @GetMapping("/{mediaId}/parts/{partNumber}")
    @Operation(
        summary = "A signed URL for one part",
        description = "Requested as the client reaches each part rather than all at once, " +
            "so a URL is never issued long before it is used and left to expire.",
    )
    fun partUrl(
        @PathVariable mediaId: UUID,
        @PathVariable partNumber: Int,
    ): PartUrlResponse = PartUrlResponse(
        partNumber = partNumber,
        url = uploads.urlForPart(mediaId, partNumber, currentUser.requireId()).toString(),
        expiresInSeconds = 900,
    )

    @GetMapping("/{mediaId}/parts")
    @Operation(
        summary = "Parts already stored",
        description = "This is what makes an upload resumable: storage keeps the parts " +
            "between requests, so a client compares this against its own progress and " +
            "sends only the difference. An upload interrupted at 90% costs the last 10%, " +
            "not all of it.",
    )
    fun parts(@PathVariable mediaId: UUID): List<UploadedPartResponse> =
        uploads.uploadedParts(mediaId, currentUser.requireId())
            .map { UploadedPartResponse(it.partNumber, it.etag, it.sizeBytes) }

    @PostMapping("/{mediaId}/complete")
    @Operation(
        summary = "Assemble the parts",
        description = "The ETags come from the client because storage issued them to the " +
            "client; they are how S3 verifies the assembly matches what it received. " +
            "Size and checksum are still read back from storage, never trusted from here.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "The object is assembled and AVAILABLE"),
        ApiResponse(
            responseCode = "422",
            description = "No parts, or the upload already finished",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun complete(
        @PathVariable mediaId: UUID,
        @Valid @RequestBody request: CompleteMultipartUploadRequest,
    ): MediaObjectResponse = MediaObjectResponse.of(
        uploads.complete(
            mediaId,
            request.parts.map { UploadedPart(it.partNumber, it.etag) },
            currentUser.requireId(),
        ),
    )

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Abandon an upload and discard its parts",
        description = "Worth calling. Parts neither completed nor aborted stay in the " +
            "bucket, do not appear in an ordinary listing, and are billed.",
    )
    fun abort(@PathVariable mediaId: UUID) = uploads.abort(mediaId, currentUser.requireId())
}
