package com.elearning.platform.media.api

import com.elearning.identity.application.UserLookupService
import com.elearning.platform.media.MediaObject
import com.elearning.platform.media.MediaStatus
import com.elearning.platform.media.application.MediaLibraryService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.ApiError
import com.elearning.shared.errors.BusinessRuleException
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "AdminMediaResponse")
data class AdminMediaResponse(
    val id: UUID,
    val originalFilename: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val status: String,
    val bucket: String,
    val uploadedById: UUID?,
    val uploadedBy: String?,
    val createdAt: Instant,
)

/**
 * The central asset library, for the dashboard's `/media` page.
 *
 * Object keys are not returned: they are generated server-side precisely so a
 * caller cannot address storage directly, and publishing them would undo that.
 * The bucket is included because "is this public or private" is the one storage
 * fact an administrator actually needs.
 */
@RestController
@RequestMapping("/api/v1/admin/media")
@Tag(name = "Admin: media", description = "The asset library")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminMediaController(
    private val library: MediaLibraryService,
    private val people: UserLookupService,
) {

    @GetMapping
    @Operation(
        summary = "Browse uploaded files",
        description = "Requires `media.read`. Filter by `status` or search `q` against " +
            "the original filename - the stored object key is generated and means " +
            "nothing to a person.",
    )
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminMediaResponse> {
        val parsed = status?.let {
            runCatching { MediaStatus.valueOf(it.uppercase()) }
                .getOrElse { throw BusinessRuleException("INVALID_STATUS", "Unknown status $status") }
        }
        val results = library.list(
            q,
            parsed,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        val uploaders = people.summaries(results.content.mapNotNull { it.createdBy })
        return PageResponse.from(results) { toResponse(it, uploaders) }
    }

    @DeleteMapping("/{mediaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete a file",
        description = "Requires `media.delete`. Refused while anything still points at " +
            "the file - a lesson, a thumbnail, a certificate, a submission or an " +
            "avatar. The database would not stop this on its own: those references " +
            "are mostly ON DELETE SET NULL, so the delete would succeed and quietly " +
            "empty whatever was using it.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Deleted from the database and from storage"),
        ApiResponse(
            responseCode = "422",
            description = "Something still references this file",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun delete(@PathVariable mediaId: UUID) = library.delete(mediaId)

    private fun toResponse(
        media: MediaObject,
        uploaders: Map<UUID, com.elearning.identity.application.PersonSummary>,
    ) = AdminMediaResponse(
        id = requireNotNull(media.id),
        originalFilename = media.originalFilename,
        mimeType = media.mimeType,
        sizeBytes = media.sizeBytes,
        status = media.status.name,
        bucket = media.bucket,
        uploadedById = media.createdBy,
        uploadedBy = media.createdBy?.let { uploaders[it]?.label },
        createdAt = media.createdAt,
    )
}
