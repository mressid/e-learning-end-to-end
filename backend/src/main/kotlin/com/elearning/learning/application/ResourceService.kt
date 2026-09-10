package com.elearning.learning.application

import com.elearning.learning.domain.CourseResource
import com.elearning.learning.domain.CourseResourceId
import com.elearning.learning.domain.ItemResource
import com.elearning.learning.domain.ItemResourceId
import com.elearning.learning.domain.RelationshipType
import com.elearning.learning.domain.Resource
import com.elearning.learning.domain.ResourceContent
import com.elearning.learning.domain.ResourceContentType
import com.elearning.learning.domain.ResourceFile
import com.elearning.learning.domain.ResourceType
import com.elearning.learning.domain.ResourceUrl
import com.elearning.learning.domain.SectionResource
import com.elearning.learning.domain.SectionResourceId
import com.elearning.learning.domain.SourceType
import com.elearning.learning.infrastructure.CourseResourceRepository
import com.elearning.learning.infrastructure.ItemResourceRepository
import com.elearning.learning.infrastructure.ResourceContentRepository
import com.elearning.learning.infrastructure.ResourceFileRepository
import com.elearning.learning.infrastructure.ResourceRepository
import com.elearning.learning.infrastructure.ResourceUrlRepository
import com.elearning.learning.infrastructure.SectionResourceRepository
import com.elearning.platform.media.MediaService
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

/**
 * Reusable learning materials and where they hang.
 *
 * A resource exists on its own; attaching it to a course, section or item is a
 * separate act with its own authorization.
 */
@Service
class ResourceService(
    private val resources: ResourceRepository,
    private val files: ResourceFileRepository,
    private val urls: ResourceUrlRepository,
    private val contents: ResourceContentRepository,
    private val courseAttachments: CourseResourceRepository,
    private val sectionAttachments: SectionResourceRepository,
    private val itemAttachments: ItemResourceRepository,
    private val sections: com.elearning.courses.infrastructure.CourseSectionRepository,
    private val catalog: CourseCatalog,
    private val mediaService: MediaService,
    private val enrollmentService: EnrollmentService,
) {

    @Transactional
    fun create(command: CreateResourceCommand, creatorId: UUID): Resource {
        val resource = resources.save(
            Resource(
                title = command.title,
                resourceType = command.resourceType,
                sourceType = command.sourceType,
                createdBy = creatorId,
                description = command.description,
            ),
        )
        val resourceId = requireNotNull(resource.id)

        // Each source type has exactly one place its content can live; a
        // resource with no backing row would be unreadable forever.
        when (command.sourceType) {
            SourceType.FILE -> {
                val mediaId = command.mediaId
                    ?: throw BusinessRuleException("MEDIA_REQUIRED", "A FILE resource needs a mediaId")
                val media = mediaService.requireAvailable(mediaId)
                if (media.createdBy != creatorId) {
                    throw ForbiddenException("MEDIA_ACCESS_DENIED", "That file is not yours to use")
                }
                files.save(
                    ResourceFile(
                        resourceId = resourceId,
                        mediaId = mediaId,
                        filename = media.originalFilename,
                        mimeType = media.mimeType,
                        extension = media.originalFilename?.substringAfterLast('.', "")?.ifBlank { null },
                        sizeBytes = media.sizeBytes,
                    ),
                )
            }
            SourceType.URL -> {
                val url = command.url?.trim()
                if (url.isNullOrBlank()) {
                    throw BusinessRuleException("URL_REQUIRED", "A URL resource needs a url")
                }
                // Only http(s): a javascript: or data: link would be handed
                // straight to a student's browser.
                if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    throw BusinessRuleException("INVALID_URL", "Only http and https URLs are allowed")
                }
                urls.save(ResourceUrl(resourceId = resourceId, url = url))
            }
            SourceType.INLINE -> {
                val body = command.content
                    ?: throw BusinessRuleException("CONTENT_REQUIRED", "An INLINE resource needs content")
                contents.save(
                    ResourceContent(
                        resourceId = resourceId,
                        contentType = command.contentType ?: ResourceContentType.MARKDOWN,
                        content = body,
                    ),
                )
            }
        }
        return resource
    }

    /**
     * Edits a resource in place.
     *
     * A resource was write-once until now, which was tenable while it was a
     * whole lesson's body saved through [LessonService] and untenable the
     * moment an item became an ordered list of them: a text block you can add
     * and never correct is not a block anyone would use.
     *
     * `sourceType` is fixed, as it is everywhere else - the content lives in a
     * different table for each, so changing it is a different resource rather
     * than an edit of this one.
     */
    @Transactional
    fun update(resourceId: UUID, command: UpdateResourceCommand, editorId: UUID): Resource {
        val resource = requireResource(resourceId)
        requireCanEdit(resource, editorId)

        command.title?.let { resource.title = it }
        if (command.describes) resource.description = command.description

        when (resource.sourceType) {
            SourceType.INLINE -> {
                val body = command.content
                if (body != null) {
                    val existing = contents.findById(resourceId).orElse(null)
                        ?: throw NotFoundException("RESOURCE_CONTENT_MISSING", "Resource content not found")
                    if (body.isBlank()) {
                        throw BusinessRuleException("CONTENT_REQUIRED", "An INLINE resource needs content")
                    }
                    existing.content = body
                    command.contentType?.let { existing.contentType = it }
                } else if (command.contentType != null) {
                    contents.findById(resourceId).orElse(null)?.contentType = command.contentType
                }
            }
            SourceType.URL -> {
                val url = command.url?.trim()
                if (url != null) {
                    if (url.isBlank()) {
                        throw BusinessRuleException("URL_REQUIRED", "A URL resource needs a url")
                    }
                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        throw BusinessRuleException("INVALID_URL", "Only http and https URLs are allowed")
                    }
                    urls.findById(resourceId).orElse(null)?.url = url
                }
            }
            // The bytes of a file are not editable; replacing them is a new
            // upload and a new resource. Only the labelling above applies.
            SourceType.FILE -> Unit
        }

        resource.touch()
        return resource
    }

    @Transactional(readOnly = true)
    fun view(resourceId: UUID, viewerId: UUID): ResourceView {
        val resource = requireResource(resourceId)
        requireCanRead(resource, viewerId)
        return buildView(resource)
    }

    /** A download URL for a FILE resource, granted on the strength of access above. */
    @Transactional(readOnly = true)
    fun downloadUrl(resourceId: UUID, viewerId: UUID): URI {
        val resource = requireResource(resourceId)
        requireCanRead(resource, viewerId)
        if (resource.sourceType != SourceType.FILE) {
            throw BusinessRuleException("NOT_A_FILE_RESOURCE", "This resource has no downloadable file")
        }
        val file = files.findById(resourceId)
            .orElseThrow { NotFoundException("RESOURCE_FILE_MISSING", "Resource file not found") }
        return mediaService.downloadUrlForAuthorizedCaller(file.mediaId)
    }

    // ---- attachment ------------------------------------------------------

    @Transactional
    fun attachToCourse(courseId: UUID, command: AttachCommand, editorId: UUID): CourseResource {
        requireEditor(courseId, editorId)
        requireResource(command.resourceId)
        val id = CourseResourceId(courseId, command.resourceId)
        return courseAttachments.findById(id).orElseGet {
            courseAttachments.save(
                CourseResource(id, command.relationshipType, courseAttachments.maxPosition(courseId) + 1),
            )
        }
    }

    @Transactional
    fun attachToSection(sectionId: UUID, command: AttachCommand, editorId: UUID): SectionResource {
        requireEditor(courseOfSection(sectionId), editorId)
        requireResource(command.resourceId)
        val id = SectionResourceId(sectionId, command.resourceId)
        return sectionAttachments.findById(id).orElseGet {
            sectionAttachments.save(
                SectionResource(id, command.relationshipType, sectionAttachments.maxPosition(sectionId) + 1),
            )
        }
    }

    @Transactional
    fun attachToItem(itemId: UUID, command: AttachCommand, editorId: UUID): ItemResource {
        requireEditor(courseOfItem(itemId), editorId)
        requireResource(command.resourceId)
        val id = ItemResourceId(itemId, command.resourceId)
        return itemAttachments.findById(id).orElseGet {
            itemAttachments.save(
                ItemResource(id, command.relationshipType, itemAttachments.maxPosition(itemId) + 1),
            )
        }
    }

    @Transactional
    fun detachFromCourse(courseId: UUID, resourceId: UUID, editorId: UUID) {
        requireEditor(courseId, editorId)
        courseAttachments.deleteById(CourseResourceId(courseId, resourceId))
    }

    /**
     * Detaching only. The resource itself survives, because it is a library
     * material that other courses may be using and this is a statement about
     * one item's contents rather than about the material.
     */
    @Transactional
    fun detachFromItem(itemId: UUID, resourceId: UUID, editorId: UUID) {
        requireEditor(courseOfItem(itemId), editorId)
        itemAttachments.deleteById(ItemResourceId(itemId, resourceId))
    }

    @Transactional
    fun detachFromSection(sectionId: UUID, resourceId: UUID, editorId: UUID) {
        requireEditor(courseOfSection(sectionId), editorId)
        sectionAttachments.deleteById(SectionResourceId(sectionId, resourceId))
    }

    /**
     * Puts an item's blocks in the order given.
     *
     * The whole order is sent rather than one move, for the reason the
     * curriculum's own reorder already documents: two authors dragging at once
     * with relative moves converge on something neither of them chose, while a
     * whole sequence is a statement of what the order *is*.
     *
     * Positions are rewritten from zero, so the stored numbers stay dense no
     * matter how many times things have been added and removed.
     */
    @Transactional
    fun reorderItemResources(itemId: UUID, orderedResourceIds: List<UUID>, editorId: UUID) {
        requireEditor(courseOfItem(itemId), editorId)

        val attached = itemAttachments.findByIdCourseItemIdOrderByPosition(itemId)
        val known = attached.associateBy { it.id.resourceId }

        // Naming something that is not attached is a stale client working from
        // an order that has since changed. Renumbering the rest around it would
        // silently drop whatever it thought it was moving.
        val unknown = orderedResourceIds.filterNot(known::containsKey)
        if (unknown.isNotEmpty()) {
            throw BusinessRuleException(
                "RESOURCE_NOT_ATTACHED",
                "Some of those resources are not attached to this item",
            )
        }
        if (orderedResourceIds.size != orderedResourceIds.distinct().size) {
            throw BusinessRuleException("DUPLICATE_RESOURCE", "That order names a resource twice")
        }

        orderedResourceIds.forEachIndexed { index, resourceId ->
            known.getValue(resourceId).position = index
        }
        // Anything the caller left out keeps its relative order behind the
        // named ones rather than being renumbered into an arbitrary spot.
        attached.filterNot { orderedResourceIds.contains(it.id.resourceId) }
            .forEachIndexed { index, row -> row.position = orderedResourceIds.size + index }
    }

    @Transactional(readOnly = true)
    fun listForCourse(courseId: UUID, viewerId: UUID): List<AttachedResource> {
        requireParticipant(courseId, viewerId)
        return courseAttachments.findByIdCourseIdOrderByPosition(courseId)
            .map { AttachedResource(buildView(requireResource(it.id.resourceId)), it.relationshipType, it.position) }
    }

    @Transactional(readOnly = true)
    fun listForSection(sectionId: UUID, viewerId: UUID): List<AttachedResource> {
        requireParticipant(courseOfSection(sectionId), viewerId)
        return sectionAttachments.findByIdSectionIdOrderByPosition(sectionId)
            .map { AttachedResource(buildView(requireResource(it.id.resourceId)), it.relationshipType, it.position) }
    }

    @Transactional(readOnly = true)
    fun listForItem(itemId: UUID, viewerId: UUID): List<AttachedResource> {
        requireParticipant(courseOfItem(itemId), viewerId)
        return itemAttachments.findByIdCourseItemIdOrderByPosition(itemId)
            .map { AttachedResource(buildView(requireResource(it.id.resourceId)), it.relationshipType, it.position) }
    }

    // ---- internals -------------------------------------------------------

    private fun buildView(resource: Resource): ResourceView {
        val id = requireNotNull(resource.id)
        val content = contents.findById(id).orElse(null)
        val file = files.findById(id).orElse(null)
        return ResourceView(
            id = id,
            title = resource.title,
            description = resource.description,
            resourceType = resource.resourceType,
            sourceType = resource.sourceType,
            url = urls.findById(id).orElse(null)?.url,
            content = content?.content,
            contentType = content?.contentType,
            filename = file?.filename,
            sizeBytes = file?.sizeBytes,
        )
    }

    private fun requireResource(resourceId: UUID): Resource = resources.findById(resourceId)
        .orElseThrow { NotFoundException("RESOURCE_NOT_FOUND", "Resource not found") }

    /**
     * The creator always can. Otherwise it depends on being a participant of a
     * course the resource is attached to - a resource is not public just because
     * somebody knows its id.
     */
    private fun requireCanRead(resource: Resource, viewerId: UUID) {
        if (resource.createdBy == viewerId) return
        val resourceId = requireNotNull(resource.id)

        // Only this resource's attachments are loaded. Scanning every
        // attachment row would degrade as the platform grows.
        val reachable = courseAttachments.findByIdResourceId(resourceId)
            .any { isParticipant(it.id.courseId, viewerId) } ||
            sectionAttachments.findByIdResourceId(resourceId)
                .any { isParticipant(courseOfSection(it.id.sectionId), viewerId) } ||
            itemAttachments.findByIdResourceId(resourceId)
                .any { isParticipant(courseOfItem(it.id.courseItemId), viewerId) }

        if (!reachable) {
            throw ForbiddenException("RESOURCE_ACCESS_DENIED", "You cannot access this resource")
        }
    }

    /**
     * Who may change a resource, as opposed to read it.
     *
     * Deliberately stricter than [requireCanRead], and not the same question.
     * A resource is shared by design - the same cheat sheet hangs off two
     * courses - so "can edit some course it is attached to" would let an editor
     * of one course silently rewrite what another course's students are
     * looking at. Nobody would see it happen.
     *
     * So editing requires being able to edit *every* course it reaches. A block
     * used only in your own course is yours to change, which is the ordinary
     * case and the one the block list depends on; a material somebody else has
     * taken into their course stops being unilaterally editable, and changing
     * it for yourself means making your own copy.
     *
     * The creator is not exempt. Handing a resource to another course and then
     * editing it from underneath them is the same problem regardless of who
     * first uploaded it.
     */
    private fun requireCanEdit(resource: Resource, editorId: UUID) {
        val resourceId = requireNotNull(resource.id)

        val courses = buildSet {
            courseAttachments.findByIdResourceId(resourceId).forEach { add(it.id.courseId) }
            sectionAttachments.findByIdResourceId(resourceId).forEach { add(courseOfSection(it.id.sectionId)) }
            itemAttachments.findByIdResourceId(resourceId).forEach { add(courseOfItem(it.id.courseItemId)) }
        }

        // Attached to nothing yet: a block just created and not yet placed, or
        // a library material nobody has used. Its creator still owns it.
        if (courses.isEmpty()) {
            if (resource.createdBy != editorId) {
                throw ForbiddenException("RESOURCE_ACCESS_DENIED", "That resource is not yours to change")
            }
            return
        }

        if (!courses.all { catalog.canEdit(it, editorId) }) {
            throw ForbiddenException(
                "RESOURCE_SHARED",
                "This material is used by a course you cannot edit. Copy it to change it here.",
            )
        }
    }

    private fun isParticipant(courseId: UUID, userId: UUID): Boolean =
        catalog.canEdit(courseId, userId) || enrollmentService.hasActiveEnrollment(courseId, userId)

    private fun requireParticipant(courseId: UUID, userId: UUID) {
        if (!isParticipant(courseId, userId)) {
            throw ForbiddenException("NOT_A_PARTICIPANT", "You are not part of this course")
        }
    }

    private fun requireEditor(courseId: UUID, editorId: UUID) {
        if (!catalog.canEdit(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }
    }

    private fun courseOfSection(sectionId: UUID): UUID = sections.findById(sectionId)
        .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        .courseId

    private fun courseOfItem(itemId: UUID): UUID = catalog.courseIdOfItem(itemId)
        ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")

}

data class CreateResourceCommand(
    val title: String,
    val resourceType: ResourceType,
    val sourceType: SourceType,
    val description: String? = null,
    val mediaId: UUID? = null,
    val url: String? = null,
    val content: String? = null,
    val contentType: ResourceContentType? = null,
)

/**
 * A partial edit: every field is optional and null means "leave it alone".
 *
 * That is the opposite of [SaveLessonCommand], where a missing field clears
 * what was there. The difference is deliberate and follows the verb. A lesson
 * is PUT, replacing the whole thing; a block is PATCHed, changing the part you
 * named. Clearing a description therefore cannot be said by omission, which is
 * what `describes` is for.
 */
data class UpdateResourceCommand(
    val title: String? = null,
    /** Whether `description` was sent at all, so that null can mean "clear it". */
    val describes: Boolean = false,
    val description: String? = null,
    val url: String? = null,
    val content: String? = null,
    val contentType: ResourceContentType? = null,
)

data class AttachCommand(
    val resourceId: UUID,
    val relationshipType: RelationshipType = RelationshipType.RESOURCE,
)

data class ResourceView(
    val id: UUID,
    val title: String,
    val description: String?,
    val resourceType: ResourceType,
    val sourceType: SourceType,
    val url: String?,
    val content: String?,
    val contentType: ResourceContentType?,
    val filename: String?,
    val sizeBytes: Long?,
)

data class AttachedResource(
    val resource: ResourceView,
    val relationshipType: RelationshipType,
    val position: Int,
)
