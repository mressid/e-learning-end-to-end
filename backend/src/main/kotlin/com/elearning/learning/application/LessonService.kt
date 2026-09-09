package com.elearning.learning.application

import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.Lesson
import com.elearning.learning.domain.Resource
import com.elearning.learning.domain.ResourceContent
import com.elearning.learning.domain.ResourceContentType
import com.elearning.learning.domain.ResourceFile
import com.elearning.learning.domain.ResourceType
import com.elearning.learning.domain.ResourceUrl
import com.elearning.learning.domain.SourceType
import com.elearning.learning.infrastructure.LessonRepository
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
        val previous = existing?.let { resources.findById(it.primaryResourceId).orElse(null) }

        val resource = writeMaterial(courseId, command, previous, editorId)

        val lesson = existing
            ?.apply {
                primaryResourceId = requireNotNull(resource.id)
                description = command.description
                durationSeconds = command.durationSeconds
                completionRule = command.completionRule ?: completionRule
            }
            ?: lessons.save(
                Lesson(
                    courseItemId = itemId,
                    primaryResourceId = requireNotNull(resource.id),
                    description = command.description,
                    durationSeconds = command.durationSeconds,
                    completionRule = command.completionRule ?: CompletionRule.MANUAL,
                ),
            )

        return view(lesson, resource)
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
        val material = requireMaterial(lesson)
        if (material.sourceType != SourceType.FILE) {
            throw BusinessRuleException("LESSON_HAS_NO_FILE", "This lesson has no downloadable file")
        }
        val file = files.findById(lesson.primaryResourceId)
            .orElseThrow { NotFoundException("LESSON_FILE_MISSING", "Lesson file not found") }
        return mediaService.downloadUrlForAuthorizedCaller(file.mediaId)
    }

    /** The media behind a lesson, for the playback path. Null unless it is a file. */
    @Transactional(readOnly = true)
    fun fileMediaIdOf(itemId: UUID): UUID? {
        val lesson = lessons.findById(itemId).orElse(null) ?: return null
        return files.findById(lesson.primaryResourceId).orElse(null)?.mediaId
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

    private fun requireMaterial(lesson: Lesson): Resource =
        resources.findById(lesson.primaryResourceId)
            .orElseThrow { NotFoundException("LESSON_CONTENT_MISSING", "Lesson content not found") }

    private fun view(lesson: Lesson, material: Resource): LessonView {
        val resourceId = lesson.primaryResourceId
        val inline = if (material.sourceType == SourceType.INLINE) {
            contents.findById(resourceId).orElse(null)
        } else {
            null
        }
        return LessonView(
            courseItemId = lesson.courseItemId,
            title = material.title,
            resourceType = material.resourceType,
            sourceType = material.sourceType,
            description = lesson.description,
            durationSeconds = lesson.durationSeconds,
            completionRule = lesson.completionRule,
            content = inline?.content,
            contentFormat = inline?.contentType,
            url = if (material.sourceType == SourceType.URL) {
                urls.findById(resourceId).orElse(null)?.url
            } else {
                null
            },
            // Media ids stay internal: readers get a URL from contentUrl().
            hasFile = material.sourceType == SourceType.FILE && files.findById(resourceId).isPresent,
        )
    }
}

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
    val title: String,
    val resourceType: ResourceType,
    val sourceType: SourceType,
    val description: String?,
    val durationSeconds: Int?,
    val completionRule: CompletionRule,
    val content: String?,
    val contentFormat: ResourceContentType?,
    val url: String?,
    val hasFile: Boolean,
)
