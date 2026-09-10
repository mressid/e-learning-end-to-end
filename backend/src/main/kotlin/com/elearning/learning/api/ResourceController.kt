package com.elearning.learning.api

import com.elearning.learning.application.AttachCommand
import com.elearning.learning.application.AttachedResource
import com.elearning.learning.application.CreateResourceCommand
import com.elearning.learning.application.ResourceService
import com.elearning.learning.application.ResourceView
import com.elearning.learning.application.UpdateResourceCommand
import com.elearning.learning.domain.RelationshipType
import com.elearning.learning.domain.ResourceContentType
import com.elearning.learning.domain.ResourceType
import com.elearning.learning.domain.SourceType
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
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(
    name = "CreateResourceRequest",
    description = "sourceType decides which content field is required: FILE→mediaId, URL→url, INLINE→content",
)
data class CreateResourceRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val resourceType: ResourceType,
    val sourceType: SourceType,
    val description: String? = null,
    val mediaId: UUID? = null,
    @field:Size(max = 2048) val url: String? = null,
    val content: String? = null,
    val contentType: ResourceContentType? = null,
)

@Schema(
    name = "UpdateResourceRequest",
    description = """
        A partial edit: a field left out is left alone, unlike the lesson
        endpoint where a missing field is a cleared one. `sourceType` cannot
        change - the content of each lives in a different table, so that is a
        different resource rather than an edit of this one - and neither can
        the bytes of a file, which is a fresh upload.
    """,
)
data class UpdateResourceRequest(
    @field:Size(max = 255) val title: String? = null,
    val description: String? = null,
    @field:Size(max = 2048) val url: String? = null,
    val content: String? = null,
    val contentType: ResourceContentType? = null,
)

@Schema(
    name = "ReorderItemResourcesRequest",
    description = "The blocks of an item, in the order they should be read. " +
        "Anything attached but not named keeps its relative order behind them.",
)
data class ReorderItemResourcesRequest(
    val resourceIds: List<UUID> = emptyList(),
)

@Schema(name = "ResourceResponse")
data class ResourceResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val resourceType: String,
    val sourceType: String,
    val url: String?,
    val content: String?,
    val contentType: String?,
    val filename: String?,
    val sizeBytes: Long?,
) {
    companion object {
        fun of(v: ResourceView) = ResourceResponse(
            id = v.id,
            title = v.title,
            description = v.description,
            resourceType = v.resourceType.name,
            sourceType = v.sourceType.name,
            url = v.url,
            content = v.content,
            contentType = v.contentType?.name,
            filename = v.filename,
            sizeBytes = v.sizeBytes,
        )
    }
}

@Schema(name = "AttachResourceRequest")
data class AttachResourceRequest(
    val resourceId: UUID,
    @get:Schema(description = "Why it is attached, e.g. REQUIRED, SOLUTION, READING")
    val relationshipType: RelationshipType = RelationshipType.RESOURCE,
)

@Schema(name = "AttachedResourceResponse")
data class AttachedResourceResponse(
    val resource: ResourceResponse,
    val relationshipType: String,
    val position: Int,
) {
    companion object {
        fun of(a: AttachedResource) =
            AttachedResourceResponse(ResourceResponse.of(a.resource), a.relationshipType.name, a.position)
    }
}

@Schema(name = "ResourceDownloadUrlResponse")
data class ResourceDownloadUrlResponse(val downloadUrl: String, val expiresInSeconds: Long)

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Resources", description = "Reusable learning materials and where they are attached")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class ResourceController(
    private val resources: ResourceService,
    private val currentUser: CurrentUser,
    private val storageProperties: StorageProperties,
) {

    @PostMapping("/resources")
    @Operation(
        summary = "Create a resource",
        description = "Independent of any course; attach it afterwards.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created"),
        ApiResponse(
            responseCode = "422",
            description = "Content missing for the chosen sourceType, or a non-http URL",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun create(@Valid @RequestBody request: CreateResourceRequest): ResponseEntity<ResourceResponse> {
        val resource = resources.create(
            CreateResourceCommand(
                title = request.title,
                resourceType = request.resourceType,
                sourceType = request.sourceType,
                description = request.description,
                mediaId = request.mediaId,
                url = request.url,
                content = request.content,
                contentType = request.contentType,
            ),
            creatorId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(
            ResourceResponse.of(resources.view(requireNotNull(resource.id), currentUser.requireId())),
        )
    }

    @GetMapping("/resources/{resourceId}")
    @Operation(summary = "Read a resource", description = "Its creator, or a participant of a course it is attached to.")
    fun get(@PathVariable resourceId: UUID): ResourceResponse =
        ResourceResponse.of(resources.view(resourceId, currentUser.requireId()))

    @PatchMapping("/resources/{resourceId}")
    @Operation(
        summary = "Edit a resource",
        description = "Allowed only to someone who can edit every course the resource is " +
            "attached to. A material shared into a course you cannot edit is not yours to " +
            "change from here, because nobody on the other side would see it happen.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Updated"),
        ApiResponse(
            responseCode = "403",
            description = "Not yours, or shared with a course you cannot edit",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun update(
        @PathVariable resourceId: UUID,
        @Valid @RequestBody request: UpdateResourceRequest,
    ): ResourceResponse {
        resources.update(
            resourceId,
            UpdateResourceCommand(
                title = request.title,
                // An empty string is how a description is removed, since a
                // missing field on a PATCH means "leave it alone" and there is
                // otherwise no way to say "make it nothing". Jackson cannot
                // tell an absent key from an explicit null here without giving
                // up the typed request entirely, and a blank description and no
                // description are the same thing to every reader of one.
                describes = request.description != null,
                description = request.description?.takeIf { it.isNotBlank() },
                url = request.url,
                content = request.content,
                contentType = request.contentType,
            ),
            editorId = currentUser.requireId(),
        )
        return ResourceResponse.of(resources.view(resourceId, currentUser.requireId()))
    }

    @GetMapping("/resources/{resourceId}/download-url")
    @Operation(summary = "Short-lived URL for a FILE resource")
    fun downloadUrl(@PathVariable resourceId: UUID): ResourceDownloadUrlResponse =
        ResourceDownloadUrlResponse(
            downloadUrl = resources.downloadUrl(resourceId, currentUser.requireId()).toString(),
            expiresInSeconds = storageProperties.presignedUrlTtl.seconds,
        )

    // ---- attachment at the three scopes ----------------------------------

    @PostMapping("/courses/{courseId}/resources")
    @Operation(summary = "Attach a resource to a course")
    fun attachToCourse(
        @PathVariable courseId: UUID,
        @Valid @RequestBody request: AttachResourceRequest,
    ): ResponseEntity<Unit> {
        resources.attachToCourse(
            courseId,
            AttachCommand(request.resourceId, request.relationshipType),
            currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping("/courses/{courseId}/resources")
    @Operation(summary = "A course's resources, in order")
    fun courseResources(@PathVariable courseId: UUID): List<AttachedResourceResponse> =
        resources.listForCourse(courseId, currentUser.requireId()).map(AttachedResourceResponse::of)

    @DeleteMapping("/courses/{courseId}/resources/{resourceId}")
    @Operation(summary = "Detach a resource", description = "The resource itself is not deleted.")
    fun detachFromCourse(
        @PathVariable courseId: UUID,
        @PathVariable resourceId: UUID,
    ): ResponseEntity<Unit> {
        resources.detachFromCourse(courseId, resourceId, currentUser.requireId())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/sections/{sectionId}/resources")
    @Operation(summary = "Attach a resource to a section")
    fun attachToSection(
        @PathVariable sectionId: UUID,
        @Valid @RequestBody request: AttachResourceRequest,
    ): ResponseEntity<Unit> {
        resources.attachToSection(
            sectionId,
            AttachCommand(request.resourceId, request.relationshipType),
            currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping("/sections/{sectionId}/resources")
    @Operation(summary = "A section's resources, in order")
    fun sectionResources(@PathVariable sectionId: UUID): List<AttachedResourceResponse> =
        resources.listForSection(sectionId, currentUser.requireId()).map(AttachedResourceResponse::of)

    @DeleteMapping("/sections/{sectionId}/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Take a resource off a section",
        description = "The material itself survives; other courses may be using it.",
    )
    fun detachFromSection(@PathVariable sectionId: UUID, @PathVariable resourceId: UUID) =
        resources.detachFromSection(sectionId, resourceId, currentUser.requireId())

    @PostMapping("/items/{itemId}/resources")
    @Operation(summary = "Attach a resource to a course item")
    fun attachToItem(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: AttachResourceRequest,
    ): ResponseEntity<Unit> {
        resources.attachToItem(
            itemId,
            AttachCommand(request.resourceId, request.relationshipType),
            currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    @GetMapping("/items/{itemId}/resources")
    @Operation(
        summary = "An item's resources, in order",
        description = "This is the item's content, read top to bottom: the blocks a lesson " +
            "is made of rather than a list of extras beside it.",
    )
    fun itemResources(@PathVariable itemId: UUID): List<AttachedResourceResponse> =
        resources.listForItem(itemId, currentUser.requireId()).map(AttachedResourceResponse::of)

    @DeleteMapping("/items/{itemId}/resources/{resourceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Take a resource off an item",
        description = "Removes the block from this item. The material itself survives, " +
            "because it is a library material other courses may be using.",
    )
    fun detachFromItem(@PathVariable itemId: UUID, @PathVariable resourceId: UUID) =
        resources.detachFromItem(itemId, resourceId, currentUser.requireId())

    @PutMapping("/items/{itemId}/resources/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Set the order of an item's blocks",
        description = "The whole sequence is sent rather than one move: two authors " +
            "dragging at once with relative moves converge on an order neither chose.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Reordered"),
        ApiResponse(
            responseCode = "422",
            description = "Names a resource that is not attached, or names one twice",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun reorderItemResources(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: ReorderItemResourcesRequest,
    ) = resources.reorderItemResources(itemId, request.resourceIds, currentUser.requireId())
}
