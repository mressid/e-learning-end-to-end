package com.elearning.assessment.application

import java.util.UUID

/**
 * What assessment needs from the rest of the platform, and nothing more.
 *
 * Declared by the consumer so assessment never touches `Course` or `Enrollment`
 * entities. The adapter in `assessment.infrastructure` delegates to the courses
 * and learning modules, keeping the dependency one-directional (§8).
 */
interface LearningContext {

    fun courseIdOfItem(itemId: UUID): UUID?

    fun itemType(itemId: UUID): String?

    /** Titles, so an administrative listing names the work rather than its id. */
    fun courseTitle(courseId: UUID): String?

    fun itemTitle(itemId: UUID): String?

    fun canEditCourse(courseId: UUID, userId: UUID): Boolean

    /** Throws if the student has no active enrolment in the course. */
    fun requireActiveEnrollment(courseId: UUID, studentId: UUID)

    /** Records the item as completed, e.g. after a passing quiz score. */
    fun markItemCompleted(itemId: UUID, studentId: UUID)

    /** Records the item as started, e.g. once an assignment is handed in. */
    fun markItemInProgress(itemId: UUID, studentId: UUID)
}
