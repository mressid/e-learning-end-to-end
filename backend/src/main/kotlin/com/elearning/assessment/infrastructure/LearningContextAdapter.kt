package com.elearning.assessment.infrastructure

import com.elearning.assessment.application.LearningContext
import com.elearning.learning.application.CourseCatalog
import com.elearning.learning.application.EnrollmentService
import com.elearning.learning.application.ProgressService
import com.elearning.learning.application.RecordProgressCommand
import com.elearning.learning.domain.ProgressStatus
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The single place assessment reaches into courses and learning.
 */
@Component
class LearningContextAdapter(
    private val catalog: CourseCatalog,
    private val enrollmentService: EnrollmentService,
    private val progressService: ProgressService,
) : LearningContext {

    override fun courseIdOfItem(itemId: UUID): UUID? = catalog.courseIdOfItem(itemId)

    override fun itemType(itemId: UUID): String? = catalog.itemType(itemId)

    override fun courseTitle(courseId: UUID): String? = catalog.courseTitle(courseId)

    override fun itemTitle(itemId: UUID): String? = catalog.itemTitle(itemId)

    override fun canEditCourse(courseId: UUID, userId: UUID): Boolean = catalog.canEdit(courseId, userId)

    override fun requireActiveEnrollment(courseId: UUID, studentId: UUID) {
        enrollmentService.requireActiveEnrollment(courseId, studentId)
    }

    override fun markItemCompleted(itemId: UUID, studentId: UUID) {
        // Reuses the same use case a student's own progress call goes through,
        // so completion rules stay in one place.
        progressService.record(itemId, studentId, RecordProgressCommand(status = ProgressStatus.COMPLETED))
    }

    override fun markItemInProgress(itemId: UUID, studentId: UUID) {
        progressService.record(itemId, studentId, RecordProgressCommand(status = ProgressStatus.IN_PROGRESS))
    }
}
