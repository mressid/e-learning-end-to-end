package com.elearning.courses.application

import com.elearning.courses.domain.Course
import com.elearning.courses.domain.CourseLevel
import com.elearning.courses.domain.CourseStatus
import com.elearning.courses.infrastructure.CourseItemRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.platform.media.MediaService
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Course authoring use cases. Transactions sit on the use case, not on
 * repository calls (§14).
 */
@Service
class CourseService(
    private val courses: CourseRepository,
    private val items: CourseItemRepository,
    private val authorization: CourseAuthorization,
    private val slugGenerator: SlugGenerator,
    private val mediaService: MediaService,
    private val platformAccess: PlatformAccess,
    private val users: UserDirectory,
) {

    @Transactional
    fun create(command: CreateCourseCommand, ownerId: UUID): Course {
        val course = courses.save(
            Course(
                ownerId = ownerId,
                title = command.title,
                slug = slugGenerator.uniqueSlugFor(command.title),
                shortDescription = command.shortDescription,
                description = command.description,
                level = command.level ?: CourseLevel.ALL_LEVELS,
                language = command.language ?: "en",
            ).apply { accessDurationDays = command.accessDurationDays },
        )
        // Authoring a course is what makes someone an instructor, so record it.
        // Creation is still open to any signed-in learner; this only keeps the
        // roster honest about who actually authors, now that the roster reads a
        // flag rather than deriving itself from `courses.owner_id`.
        users.markAsInstructor(ownerId)
        return course
    }

    @Transactional(readOnly = true)
    fun getForViewer(courseId: UUID, viewerId: UUID?): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanView(course, viewerId)
        return course
    }

    @Transactional(readOnly = true)
    fun listPublished(pageable: Pageable): Page<Course> =
        courses.findByStatus(CourseStatus.PUBLISHED, pageable)

    @Transactional(readOnly = true)
    fun searchPublished(term: String, pageable: Pageable): Page<Course> =
        courses.search(term, CourseStatus.PUBLISHED.name, pageable)

    @Transactional
    fun update(courseId: UUID, command: UpdateCourseCommand, editorId: UUID): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanEdit(course, editorId)

        command.title?.let { course.title = it }
        command.shortDescription?.let { course.shortDescription = it }
        command.description?.let { course.description = it }
        command.level?.let { course.level = it }
        command.language?.let { course.language = it }
        // A future enrolment gets the new window; nobody already enrolled is
        // touched, because their expiry was fixed when they enrolled.
        command.accessDurationDays?.let { course.accessDurationDays = it }
        course.touch()
        return course
    }

    /**
     * Attaches a thumbnail.
     *
     * The image must already be uploaded and public: a private object would need
     * a signed URL that expires, which is useless on a cacheable listing page.
     */
    @Transactional
    fun setThumbnail(courseId: UUID, mediaId: UUID, editorId: UUID): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanEdit(course, editorId)

        val media = mediaService.requireAvailable(mediaId)
        val uploader = media.createdBy
        if (uploader != editorId && (uploader == null || !authorization.canEdit(course, uploader))) {
            throw ForbiddenException("MEDIA_ACCESS_DENIED", "That image is not yours to attach")
        }
        if (mediaService.publicUrlsFor(listOf(mediaId)).isEmpty()) {
            throw BusinessRuleException(
                "THUMBNAIL_MUST_BE_PUBLIC",
                "Upload the thumbnail with visibility PUBLIC",
            )
        }

        course.thumbnailMediaId = mediaId
        course.touch()
        return course
    }

    /**
     * The dashboard's view of the catalogue: every course, whatever its status.
     *
     * Separate from `listPublished` rather than a flag on it. Discovery is
     * PUBLISHED-only by design and that filter is the thing keeping drafts off
     * the public listing - making it conditional would put the decision one
     * boolean away from being wrong.
     */
    @Transactional(readOnly = true)
    fun listForAdministration(term: String?, status: CourseStatus?, owner: UUID?, pageable: Pageable): Page<Course> {
        platformAccess.require("course.read")
        return when {
            !term.isNullOrBlank() -> courses.searchAllStatuses(term.trim(), pageable)
            status != null -> courses.findByStatus(status, pageable)
            owner != null -> courses.findByOwnerId(owner, pageable)
            else -> courses.findAll(pageable)
        }
    }

    /** Counts per status, for the dashboard's overview tiles. */
    @Transactional(readOnly = true)
    fun countsByStatus(): Map<CourseStatus, Long> =
        CourseStatus.entries.associateWith { courses.countByStatus(it) }

    @Transactional
    fun publish(courseId: UUID, editorId: UUID): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanEdit(course, editorId, "course.publish")

        // The rule lives on the entity; the service supplies what the entity
        // cannot know and translates the failure into an API error.
        val itemCount = items.countByCourseId(courseId)
        try {
            course.publish(itemCount)
        } catch (ex: IllegalArgumentException) {
            throw BusinessRuleException("COURSE_NOT_PUBLISHABLE", ex.message ?: "Course cannot be published")
        }
        return course
    }

    @Transactional
    fun unpublish(courseId: UUID, editorId: UUID): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanEdit(course, editorId, "course.publish")
        course.unpublish()
        return course
    }

    @Transactional
    fun archive(courseId: UUID, editorId: UUID): Course {
        val course = findOrThrow(courseId)
        authorization.requireCanEdit(course, editorId, "course.publish")
        course.archive()
        return course
    }

    private fun findOrThrow(courseId: UUID): Course = courses.findById(courseId)
        .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
}

data class CreateCourseCommand(
    val title: String,
    val shortDescription: String?,
    val description: String?,
    val level: CourseLevel?,
    val language: String?,
    val accessDurationDays: Int? = null,
)

data class UpdateCourseCommand(
    val title: String? = null,
    val shortDescription: String? = null,
    val description: String? = null,
    val level: CourseLevel? = null,
    val language: String? = null,
    val accessDurationDays: Int? = null,
)
