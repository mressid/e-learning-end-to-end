package com.elearning.community.infrastructure

import com.elearning.community.application.CommunityContext
import com.elearning.learning.application.CourseCatalog
import com.elearning.learning.application.EnrollmentService
import com.elearning.learning.infrastructure.LessonRepository
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class CommunityContextAdapter(
    private val catalog: CourseCatalog,
    private val enrollmentService: EnrollmentService,
    private val lessons: LessonRepository,
) : CommunityContext {

    override fun courseExists(courseId: UUID): Boolean = catalog.exists(courseId)

    override fun isPublished(courseId: UUID): Boolean = catalog.isPublished(courseId)

    override fun canEditCourse(courseId: UUID, userId: UUID): Boolean = catalog.canEdit(courseId, userId)

    override fun isEnrolled(courseId: UUID, userId: UUID): Boolean =
        enrollmentService.hasActiveEnrollment(courseId, userId)

    override fun hasStudied(courseId: UUID, userId: UUID): Boolean =
        enrollmentService.hasAnyEnrollment(courseId, userId)

    override fun courseIdOfItem(itemId: UUID): UUID? = catalog.courseIdOfItem(itemId)

    override fun lessonExists(lessonId: UUID): Boolean = lessons.existsById(lessonId)
}
