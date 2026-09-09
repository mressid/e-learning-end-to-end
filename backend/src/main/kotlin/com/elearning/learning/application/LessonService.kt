package com.elearning.learning.application

import com.elearning.learning.domain.ArticleContent
import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.DocumentContent
import com.elearning.learning.domain.Lesson
import com.elearning.learning.domain.LessonContentType
import com.elearning.learning.domain.VideoContent
import com.elearning.learning.infrastructure.ArticleContentRepository
import com.elearning.learning.infrastructure.DocumentContentRepository
import com.elearning.learning.infrastructure.LessonRepository
import com.elearning.learning.infrastructure.VideoContentRepository
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
 * Who may do what:
 *   author -> course editors (owner or co-instructor)
 *   read   -> course editors, or a student with an active enrolment
 */
@Service
class LessonService(
    private val lessons: LessonRepository,
    private val videos: VideoContentRepository,
    private val articles: ArticleContentRepository,
    private val documents: DocumentContentRepository,
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

        val lesson = lessons.findById(itemId).orElse(null)
            ?.apply {
                contentType = command.contentType
                description = command.description
                durationSeconds = command.durationSeconds
                completionRule = command.completionRule ?: completionRule
            }
            ?: lessons.save(
                Lesson(
                    courseItemId = itemId,
                    contentType = command.contentType,
                    description = command.description,
                    durationSeconds = command.durationSeconds,
                    completionRule = command.completionRule ?: CompletionRule.MANUAL,
                ),
            )

        when (command.contentType) {
            LessonContentType.VIDEO -> saveVideo(itemId, courseId, command, editorId)
            LessonContentType.ARTICLE -> saveArticle(itemId, command)
            LessonContentType.DOCUMENT -> saveDocument(itemId, courseId, command, editorId)
            // The V1 schema has no content table for these, so accepting one
            // would store a lesson whose content could never be read back.
            LessonContentType.AUDIO, LessonContentType.EXTERNAL ->
                throw BusinessRuleException(
                    "CONTENT_TYPE_NOT_SUPPORTED",
                    "${command.contentType} lessons are not supported yet",
                )
        }
        return view(lesson)
    }

    @Transactional(readOnly = true)
    fun get(itemId: UUID, viewerId: UUID): LessonView {
        requireReadableLesson(itemId, viewerId)
        val lesson = lessons.findById(itemId)
            .orElseThrow { NotFoundException("LESSON_NOT_FOUND", "Lesson not found") }
        return view(lesson)
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

        val mediaId = when (lesson.contentType) {
            LessonContentType.VIDEO -> videos.findById(itemId).orElse(null)?.mediaId
            LessonContentType.DOCUMENT -> documents.findById(itemId).orElse(null)?.mediaId
            else -> null
        } ?: throw BusinessRuleException("LESSON_HAS_NO_FILE", "This lesson has no downloadable file")

        return mediaService.downloadUrlForAuthorizedCaller(mediaId)
    }

    private fun saveVideo(itemId: UUID, courseId: UUID, command: SaveLessonCommand, editorId: UUID) {
        val existing = videos.findById(itemId).orElse(null)

        // Leaving the file out of an update keeps the one already attached.
        // Media ids are deliberately never given back to the client, so an
        // author changing a video lesson's description has nothing to re-send -
        // demanding it here made every edit after the first one impossible.
        // Still required when there is no lesson yet, which is what a client
        // creating one has to satisfy.
        val mediaId = command.mediaId
            ?: existing?.mediaId
            ?: throw BusinessRuleException("MEDIA_REQUIRED", "A video lesson needs a mediaId")
        val thumbnailMediaId = command.thumbnailMediaId ?: existing?.thumbnailMediaId

        requireAttachable(mediaId, courseId, editorId)
        command.thumbnailMediaId?.let { requireAttachable(it, courseId, editorId) }

        val isNewFile = existing == null || existing.mediaId != mediaId

        if (existing == null) {
            videos.save(
                VideoContent(
                    lessonId = itemId,
                    mediaId = mediaId,
                    thumbnailMediaId = thumbnailMediaId,
                    durationSeconds = command.durationSeconds,
                ),
            )
        } else {
            // Re-pointing at a different file invalidates the renditions built
            // from the old one, so they are cleared rather than left to serve
            // the previous video under the new lesson.
            if (isNewFile) existing.hlsManifestMediaId = null
            existing.mediaId = mediaId
            existing.thumbnailMediaId = thumbnailMediaId
            existing.durationSeconds = command.durationSeconds
        }

        // Queued here rather than when the upload completes, because media has
        // no idea whether a file is a lesson video, a submission attachment or
        // a resource - transcoding every uploaded MP4 would burn CPU on files
        // nobody streams. Published after this transaction commits, so the
        // worker cannot win the race to a row that is not there yet.
        //
        // Only for a file this lesson has not encoded already: re-queueing the
        // same video every time somebody fixes a sentence in the description
        // would spend a CPU-hour on a change that cannot affect the output.
        if (isNewFile) transcoding.enqueueAndPublish(mediaId, itemId)
    }

    private fun saveArticle(itemId: UUID, command: SaveLessonCommand) {
        val body = command.content
            ?: throw BusinessRuleException("CONTENT_REQUIRED", "An article lesson needs content")
        val existing = articles.findById(itemId).orElse(null)
        if (existing == null) {
            articles.save(ArticleContent(lessonId = itemId, content = body))
        } else {
            existing.content = body
        }
    }

    private fun saveDocument(itemId: UUID, courseId: UUID, command: SaveLessonCommand, editorId: UUID) {
        val existing = documents.findById(itemId).orElse(null)

        // As with a video: omitting the file on an update keeps the one that is
        // there, because the client was never told which one it is.
        val mediaId = command.mediaId
            ?: existing?.mediaId
            ?: throw BusinessRuleException("MEDIA_REQUIRED", "A document lesson needs a mediaId")
        requireAttachable(mediaId, courseId, editorId)

        if (existing == null) {
            documents.save(DocumentContent(lessonId = itemId, mediaId = mediaId))
        } else {
            existing.mediaId = mediaId
        }
    }

    /**
     * A file may only be attached if it is fully uploaded and was uploaded by
     * somebody who can author this course. Without the second check a course
     * editor who guessed another user's media id could republish their file to
     * their own students.
     */
    private fun requireAttachable(mediaId: UUID, courseId: UUID, editorId: UUID) {
        val media = mediaService.requireAvailable(mediaId)
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

    private fun view(lesson: Lesson): LessonView = LessonView(
        courseItemId = lesson.courseItemId,
        contentType = lesson.contentType,
        description = lesson.description,
        durationSeconds = lesson.durationSeconds,
        completionRule = lesson.completionRule,
        article = articles.findById(lesson.courseItemId).orElse(null)?.content,
        // Media ids stay internal: readers get a URL from contentUrl() instead.
        hasFile = when (lesson.contentType) {
            LessonContentType.VIDEO -> videos.findById(lesson.courseItemId).orElse(null)?.mediaId != null
            LessonContentType.DOCUMENT -> documents.findById(lesson.courseItemId).orElse(null)?.mediaId != null
            else -> false
        },
    )
}

data class SaveLessonCommand(
    val contentType: LessonContentType,
    val description: String? = null,
    val durationSeconds: Int? = null,
    val completionRule: CompletionRule? = null,
    val content: String? = null,
    val mediaId: UUID? = null,
    val thumbnailMediaId: UUID? = null,
)

data class LessonView(
    val courseItemId: UUID,
    val contentType: LessonContentType,
    val description: String?,
    val durationSeconds: Int?,
    val completionRule: CompletionRule,
    val article: String?,
    val hasFile: Boolean,
)
