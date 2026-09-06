package com.elearning.platform.media.api

import com.elearning.platform.media.MediaObject
import com.elearning.platform.media.MediaVisibility
import com.elearning.platform.media.UploadTicket
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(name = "RequestUploadRequest")
data class RequestUploadRequest(
    @field:Size(max = 512)
    @get:Schema(description = "Original name, kept for display", example = "lecture-01.mp4")
    val filename: String? = null,

    @field:NotBlank @field:Size(max = 255)
    @get:Schema(
        description = "Signed into the upload URL: the client must send this exact Content-Type",
        example = "video/mp4",
    )
    val contentType: String,

    @get:Schema(
        description = "PUBLIC objects get a stable, cacheable URL and are readable by anyone. " +
            "Use it for thumbnails and other freely visible assets, never for course content.",
    )
    val visibility: MediaVisibility = MediaVisibility.PRIVATE,
)

@Schema(name = "UploadTicketResponse", description = "Upload the bytes directly to uploadUrl with HTTP PUT")
data class UploadTicketResponse(
    val mediaId: UUID,
    val uploadUrl: String,
    val expiresInSeconds: Long,
    val status: String,
) {
    companion object {
        fun of(ticket: UploadTicket) = UploadTicketResponse(
            mediaId = requireNotNull(ticket.media.id),
            uploadUrl = ticket.uploadUrl.toString(),
            expiresInSeconds = ticket.expiresIn.seconds,
            status = ticket.media.status.name,
        )
    }
}

@Schema(name = "MediaObjectResponse")
data class MediaObjectResponse(
    val id: UUID,
    val originalFilename: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val status: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(m: MediaObject) = MediaObjectResponse(
            id = requireNotNull(m.id),
            originalFilename = m.originalFilename,
            mimeType = m.mimeType,
            sizeBytes = m.sizeBytes,
            status = m.status.name,
            createdAt = m.createdAt,
        )
    }
}

@Schema(name = "DownloadUrlResponse")
data class DownloadUrlResponse(
    val downloadUrl: String,
    val expiresInSeconds: Long,
)
