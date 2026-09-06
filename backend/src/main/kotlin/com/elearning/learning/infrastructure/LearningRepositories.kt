package com.elearning.learning.infrastructure

import com.elearning.learning.domain.Enrollment
import com.elearning.learning.domain.EnrollmentStatus
import com.elearning.learning.domain.LearningProgress
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface EnrollmentRepository : JpaRepository<Enrollment, UUID> {

    fun findByStudentIdAndCourseId(studentId: UUID, courseId: UUID): Optional<Enrollment>

    /** The student's enrolments in a set of courses, for the prerequisite gate. */
    fun findByStudentIdAndCourseIdIn(studentId: UUID, courseIds: Collection<UUID>): List<Enrollment>

    fun findByStudentId(studentId: UUID, pageable: Pageable): Page<Enrollment>

    fun findByStudentIdAndStatus(studentId: UUID, status: EnrollmentStatus, pageable: Pageable): Page<Enrollment>

    fun countByCourseIdAndStatus(courseId: UUID, status: EnrollmentStatus): Long

    /** Enrolments whose access window has closed but whose status has not caught up. */
    fun findByStatusAndExpiresAtBefore(
        status: EnrollmentStatus,
        expiresAt: java.time.Instant,
        pageable: org.springframework.data.domain.Pageable,
    ): List<Enrollment>
}

interface LearningProgressRepository : JpaRepository<LearningProgress, UUID> {

    fun findByStudentIdAndCourseItemId(studentId: UUID, courseItemId: UUID): Optional<LearningProgress>

    fun findByStudentIdAndCourseId(studentId: UUID, courseId: UUID): List<LearningProgress>

    /** Whether anyone has progress against these items, for the delete guard. */
    fun existsByCourseItemIdIn(courseItemIds: Collection<UUID>): Boolean

    /** The student's records for a set of items, for the item prerequisite gate. */
    fun findByStudentIdAndCourseItemIdIn(
        studentId: UUID,
        courseItemIds: Collection<UUID>,
    ): List<LearningProgress>
}
