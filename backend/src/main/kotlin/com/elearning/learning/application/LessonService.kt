package com.elearning.learning.application

import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.Lesson
import com.elearning.learning.domain.ItemResource
import com.elearning.learning.domain.ItemResourceId
import com.elearning.learning.domain.RelationshipType
import com.elearning.learning.domain.Resource
import com.elearning.learning.domain.ResourceContent
import com.elearning.learning.domain.ResourceContentType
import com.elearning.learning.domain.ResourceFile
import com.elearning.learning.domain.ResourceType
import com.elearning.learning.domain.ResourceUrl
import com.elearning.learning.domain.SourceType
import com.elearning.learning.infrastructure.LessonRepository
import com.elearning.learning.infrastructure.ItemResourceRepository
import com.elearning.learning.infrastructure.ResourceContentRepository
import com.elearning.learning.infrastructure.ResourceFileRepository
import com.elearning.learning.infrastructure.ResourceRepository
import com.elearning.learning.infrastructure.ResourceUrlRepository
import com.elearning.platform.media.MediaService
import com.elearning.platform.transcode.application.TranscodeService
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

/**
 * Authoring and reading lesson content.
 *
 * A lesson is a description, a duration, a completion rule, and one resource
 * holding the material. The resource decides what the material is and where it
 * lives — the two questions a single `contentType` used to answer badly — so a
 * lesson can now be an audio file or a link without a table being invented for
 * each.
 *
 * Who may do what:
 *   author -> course editors (owner or co-instructor)
 *   read   -> course editors, or a student with an active enrolment
 */
@Service
class LessonService(
    private val lessons: LessonRepository,
    private val resources: ResourceRepository,
    private val files: ResourceFileRepository,
    private val urls: ResourceUrlRepository,
    private val contents: ResourceContentRepository,
    private val itemAttachments: ItemResourceRepository,
    private val catalog: CourseCatalog,
    private val enrollmentService: EnrollmentService,
    private val mediaService: MediaService,
    private val transcoding: TranscodeService,
) {

    @Transactional
    fun upsert(itemId: UUID, command: SaveLessonCommand, editorId: UUID): LessonView {
        val courseId = requireCourseOfLessonItem(itemId)
        if (!catalog.canEdit(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }

        val existing = lessons.findById(itemId).orElse(null)
        val previous = existing?.primaryResourceId?.let { resources.findById(it).orElse(null) }

        val resource = writeMaterial(courseId, command, previous, editorId)
        val resourceId = requireNotNull(resource.id)

        val lesson = existing
            ?.apply {
                primaryResourceId = resourceId
                description = command.description
                durationSeconds = command.durationSeconds
                completionRule = command.completionRule ?: completionRule
            }
            ?: lessons.save(
                Lesson(
                    courseItemId = itemId,
                    primaryResourceId = resourceId,
                    description = command.description,
                    durationSeconds = command.durationSeconds,
                    completionRule = command.completionRule ?: CompletionRule.MANUAL,
                ),
            )

        // The material is the lesson's first block, so that everything an item
        // teaches with is in one ordered list rather than one thing here and
        // the rest somewhere else. Idempotent: saving a lesson twice does not
        // attach it twice, and re-saving does not move it back to the front if
        // an author has since put something above it.
        attachAsBlock(itemId, resourceId)

        return view(lesson, resource)
    }

    /**
     * Records the primary material as a block of its item.
     *
     * Placed at the end rather than the front, because by the time this runs on
     * an existing lesson the author may have deliberately ordered things around
     * it. Only a lesson's very first save puts it at position zero, which is
     * the same place the migration put every lesson that predates blocks.
     */
    private fun attachAsBlock(itemId: UUID, resourceId: UUID) {
        val id = ItemResourceId(itemId, resourceId)
        if (itemAttachments.existsById(id)) return
        itemAttachments.save(
            ItemResource(id, RelationshipType.RESOURCE, itemAttachments.maxPosition(itemId) + 1),
        )
    }

    /**
     * Updates what belongs to the lesson rather than to its material.
     *
     * A separate verb from [upsert] on purpose. `PUT /lesson` replaces the
     * whole lesson including its material, which is right when the material is
     * what you are editing and wrong when the content is a list of blocks
     * managed through the resource endpoints - there, describing a lesson
     * should not require re-sending a video.
     *
     * Creates the lesson row if the item has none, so an item can be given
     * blocks and a description without ever naming a primary material.
     */
    @Transactional
    fun updateDetails(itemId: UUID, command: LessonDetailsCommand, editorId: UUID): LessonView {
        val courseId = requireCourseOfLessonItem(itemId)
        if (!catalog.canEdit(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }

        val lesson = lessons.findById(itemId).orElseGet {
            lessons.save(Lesson(courseItemId = itemId))
        }
        if (command.describes) lesson.description = command.description
        if (command.times) lesson.durationSeconds = command.durationSeconds
        command.completionRule?.let { lesson.completionRule = it }

        return view(lesson, lesson.primaryResourceId?.let { resources.findById(it).orElse(null) })
    }

    /**
     * Writes the material and returns the resource holding it.
     *
     * `sourceType` is fixed on a resource — its content lives in a different
     * table for each — so changing a lesson from a file to typed text makes a
     * new resource rather than editing the old one. The old one is left where
     * it is: it is a library material like any other, often an upload the
     * author made, and a lesson changing shape is not a reason to destroy it.
     * Anything else stays in place and is edited, so rewriting an article does
     * not litter the library with a resource per draft.
     */
    private fun writeMaterial(
        courseId: UUID,
        command: SaveLessonCommand,
        previous: Resource?,
        editorId: UUID,
    ): Resource {
        val resource = previous?.takeIf { it.sourceType == command.sourceType }?.apply {
            title = command.title
            resourceType = command.resourceType
            touch()
        } ?: resources.save(
            Resource(
                title = command.title,
                resourceType = command.resourceType,
                sourceType = command.sourceType,
                createdBy = editorId,
            ),
        )
        val resourceId = requireNotNull(resource.id)

        when (command.sourceType) {
            SourceType.FILE -> writeFile(resourceId, courseId, command, editorId)
            SourceType.URL -> writeUrl(resourceId, command)
            SourceType.INLINE -> writeInline(resourceId, command)
        }

        // A lesson is rarely only one thing. "Watch this, then read the notes,
        // then download the slides" is what a lesson actually looks like, and
        // making an author create three items to say it fragments one lesson's
        // progress across three rows of the curriculum.
        //
        // The three content tables are each keyed on the resource alone, so a
        // resource holding a video can hold writing beside it without a table
        // or a column being invented. `sourceType` still says what the lesson
        // *is* - it decides what a student is being asked to do - and this is
        // what accompanies it.
        if (command.sourceType != SourceType.INLINE) {
            writeNotes(resourceId, command)
        }
        return resource
    }

    private fun writeFile(
        resourceId: UUID,
        courseId: UUID,
        command: SaveLessonCommand,
        editorId: UUID,
    ) {
        val existing = files.findById(resourceId).orElse(null)

        // Leaving the file out of an update keeps the one already attached.
        // Media ids are deliberately never given back to the client, so an
        // author changing a description has nothing to re-send; demanding it
        // made every edit after the first one impossible.
        val mediaId = command.mediaId
            ?: existing?.mediaId
            ?: throw BusinessRuleException("MEDIA_REQUIRED", "A file lesson needs a mediaId")

        val media = requireAttachable(mediaId, courseId, editorId)
        val isNewFile = existing == null || existing.mediaId != mediaId

        if (existing == null) {
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
        } else if (isNewFile) {
            existing.mediaId = mediaId
            existing.filename = media.originalFilename
            existing.mimeType = media.mimeType
            existing.extension = media.originalFilename?.substringAfterLast('.', "")?.ifBlank { null }
            existing.sizeBytes = media.sizeBytes
        }

        // Queued here rather than when the upload completes, because media has
        // no idea whether a file is a lesson video, a submission attachment or
        // a reading - transcoding every uploaded MP4 would burn CPU on files
        // nobody streams. Published after this transaction commits, so the
        // worker cannot win the race to a row that is not there yet.
        //
        // Only for a video, and only for one not encoded already: re-queueing
        // the same file every time somebody fixes a sentence would spend a
        // CPU-hour on a change that cannot affect the output.
        if (command.resourceType == ResourceType.VIDEO && isNewFile) {
            transcoding.enqueueAndPublish(mediaId)
        }
    }

    private fun writeUrl(resourceId: UUID, command: SaveLessonCommand) {
        val url = command.url?.trim()
        if (url.isNullOrBlank()) {
            throw BusinessRuleException("URL_REQUIRED", "A link lesson needs a url")
        }
        // Only http(s): a javascript: or data: link would be handed straight to
        // a student's browser.
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw BusinessRuleException("INVALID_URL", "Only http and https URLs are allowed")
        }
        val existing = urls.findById(resourceId).orElse(null)
        if (existing == null) urls.save(ResourceUrl(resourceId = resourceId, url = url)) else existing.url = url
    }

    private fun writeInline(resourceId: UUID, command: SaveLessonCommand) {
        val body = command.content
            ?: throw BusinessRuleException("CONTENT_REQUIRED", "A written lesson needs content")
        val format = command.contentFormat ?: ResourceContentType.MARKDOWN
        val existing = contents.findById(resourceId).orElse(null)
        if (existing == null) {
            contents.save(ResourceContent(resourceId = resourceId, contentType = format, content = body))
        } else {
            existing.content = body
            existing.contentType = format
        }
    }

    /**
     * The writing that accompanies a file or a link, rather than being the
     * lesson itself.
     *
     * Optional, unlike [writeInline] - a video with no notes under it is a
     * perfectly ordinary lesson. Sending nothing clears what was there, which
     * is how every other field on this endpoint already behaves; the file is
     * the sole exception, and only because its media id is never handed back
     * for the client to re-send. Notes are returned in full, so an author who
     * leaves them out is saying to remove them.
     */
    private fun writeNotes(resourceId: UUID, command: SaveLessonCommand) {
        val body = command.content?.takeIf { it.isNotBlank() }
        val existing = contents.findById(resourceId).orElse(null)

        if (body == null) {
            if (existing != null) contents.delete(existing)
            return
        }

        val format = command.contentFormat ?: ResourceContentType.MARKDOWN
        if (existing == null) {
            contents.save(ResourceContent(resourceId = resourceId, contentType = format, content = body))
        } else {
            existing.content = body
            existing.contentType = format
        }
    }

    @Transactional(readOnly = true)
    fun get(itemId: UUID, viewerId: UUID): LessonView {
        requireReadableLesson(itemId, viewerId)
        val lesson = lessons.findById(itemId)
            .orElseThrow { NotFoundException("LESSON_NOT_FOUND", "Lesson not found") }
        return view(lesson, requireMaterial(lesson))
    }

    /**
     * A short-lived URL for a lesson's file.
     *
     * The student never learns the media id or the object key - they ask for
     * "this lesson's video" and access is decided from their enrolment.
     */
    @Transactional(readOnly = true)
    fun contentUrl(itemId: UUID, viewerId: UUID): URI {
        requireReadableLesson(itemId, viewerId)
        val lesson = lessons.findById(itemId)
            .orElseThrow { NotFoundException("LESSON_NOT_FOUND", "Lesson not found") }
        val material = resolveBlock(lesson) { it.sourceType == SourceType.FILE }
            ?: throw BusinessRuleException("LESSON_HAS_NO_FILE", "This lesson has no downloadable file")
        val file = files.findById(requireNotNull(material.id))
            .orElseThrow { NotFoundException("LESSON_FILE_MISSING", "Lesson file not found") }
        return mediaService.downloadUrlForAuthorizedCaller(file.mediaId)
    }

    /** The media behind a lesson, for the playback path. Null unless it has a video. */
    @Transactional(readOnly = true)
    fun fileMediaIdOf(itemId: UUID): UUID? {
        val lesson = lessons.findById(itemId).orElse(null) ?: return null
        val material = resolveBlock(lesson) {
            it.sourceType == SourceType.FILE && it.resourceType == ResourceType.VIDEO
        }
        // Falls back to any file, so a lesson whose single block is a file
        // typed as something other than VIDEO behaves as it always did.
            ?: resolveBlock(lesson) { it.sourceType == SourceType.FILE }
            ?: return null
        return files.findById(requireNotNull(material.id)).orElse(null)?.mediaId
    }

    /**
     * A file may only be attached if it is fully uploaded and was uploaded by
     * somebody who can author this course. Without the second check a course
     * editor who guessed another user's media id could republish their file to
     * their own students.
     */
    private fun requireAttachable(mediaId: UUID, courseId: UUID, editorId: UUID) =
        mediaService.requireAvailable(mediaId).also { media ->
            val uploader = media.createdBy
            if (uploader != editorId && (uploader == null || !catalog.canEdit(courseId, uploader))) {
                throw ForbiddenException("MEDIA_ACCESS_DENIED", "That media object is not yours to attach")
            }
        }

    private fun requireCourseOfLessonItem(itemId: UUID): UUID {
        val courseId = catalog.courseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        if (catalog.itemType(itemId) != "LESSON") {
            throw BusinessRuleException("NOT_A_LESSON_ITEM", "This course item is not a lesson")
        }
        return courseId
    }

    /** Editors may always read; everyone else needs an active enrolment. */
    private fun requireReadableLesson(itemId: UUID, viewerId: UUID): UUID {
        val courseId = requireCourseOfLessonItem(itemId)
        if (!catalog.canEdit(courseId, viewerId)) {
            enrollmentService.requireActiveEnrollment(courseId, viewerId)
        }
        return courseId
    }

    private fun requireMaterial(lesson: Lesson): Resource? =
        lesson.primaryResourceId?.let {
            resources.findById(it)
                .orElseThrow { NotFoundException("LESSON_CONTENT_MISSING", "Lesson content not found") }
        }

    /**
     * The block a lesson-level request means.
     *
     * The pointer if there is one, and otherwise the first block in reading
     * order that [matches] - the first video for a stream, the first file for a
     * download. First rather than only, because a lesson may hold two videos
     * and a request that named no block has to mean something; the block-level
     * endpoints exist for when it matters which.
     */
    private fun resolveBlock(lesson: Lesson, matches: (Resource) -> Boolean): Resource? {
        lesson.primaryResourceId
            ?.let { resources.findById(it).orElse(null) }
            ?.takeIf(matches)
            ?.let { return it }

        return itemAttachments.findByIdCourseItemIdOrderByPosition(lesson.courseItemId)
            .asSequence()
            .mapNotNull { resources.findById(it.id.resourceId).orElse(null) }
            .firstOrNull(matches)
    }

    /**
     * A lesson as its own row plus whichever block is its primary material.
     *
     * `material` is null for a lesson assembled entirely from blocks, which is
     * every lesson made since an item became a list of them. The fields that
     * described that one material go null with it: there is no single answer
     * to "what kind of thing is this lesson" once it is a video and some notes
     * and two downloads. Read the blocks for that.
     */
    private fun view(lesson: Lesson, material: Resource?): LessonView {
        val resourceId = material?.id
        // Read whatever the sourceType, because writing is no longer only ever
        // the lesson itself: for an INLINE lesson this is the body, and for a
        // file or a link it is the notes that go with it.
        val written = resourceId?.let { contents.findById(it).orElse(null) }
        return LessonView(
            courseItemId = lesson.courseItemId,
            title = material?.title,
            resourceType = material?.resourceType,
            sourceType = material?.sourceType,
            description = lesson.description,
            durationSeconds = lesson.durationSeconds,
            completionRule = lesson.completionRule,
            content = written?.content,
            contentFormat = written?.contentType,
            url = if (material?.sourceType == SourceType.URL && resourceId != null) {
                urls.findById(resourceId).orElse(null)?.url
            } else {
                null
            },
            // Media ids stay internal: readers get a URL from contentUrl().
            hasFile = material?.sourceType == SourceType.FILE &&
                resourceId != null &&
                files.findById(resourceId).isPresent,
        )
    }
}

/**
 * The lesson's own fields, each optional so a PATCH can name just one.
 *
 * `describes` and `times` exist because null is a meaningful value for both
 * of the fields they guard - no description, and no stated duration - so
 * absence and null have to be told apart.
 */
data class LessonDetailsCommand(
    val describes: Boolean = false,
    val description: String? = null,
    val times: Boolean = false,
    val durationSeconds: Int? = null,
    val completionRule: CompletionRule? = null,
)

data class SaveLessonCommand(
    val title: String,
    val resourceType: ResourceType,
    val sourceType: SourceType,
    val description: String? = null,
    val durationSeconds: Int? = null,
    val completionRule: CompletionRule? = null,
    val content: String? = null,
    val contentFormat: ResourceContentType? = null,
    val url: String? = null,
    val mediaId: UUID? = null,
)

data class LessonView(
    val courseItemId: UUID,
    /** Null once a lesson is a list of blocks rather than one material. */
    val title: String?,
    val resourceType: ResourceType?,
    val sourceType: SourceType?,
    val description: String?,
    val durationSeconds: Int?,
    val completionRule: CompletionRule,
    val content: String?,
    val contentFormat: ResourceContentType?,
    val url: String?,
    val hasFile: Boolean,
)
