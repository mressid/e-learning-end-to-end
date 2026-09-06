package com.elearning.learning.application

import com.elearning.learning.domain.Enrollment
import com.elearning.learning.domain.EnrollmentStatus
import com.elearning.learning.infrastructure.EnrollmentRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Service
class EnrollmentService(
    private val enrollments: EnrollmentRepository,
    private val catalog: CourseCatalog,
) {

    @Transactional
    fun enroll(courseId: UUID, studentId: UUID): Enrollment {
        if (!catalog.exists(courseId)) {
            throw NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
        // Enrolling in a draft would let a student see unpublished material.
        if (!catalog.isPublished(courseId)) {
            throw BusinessRuleException("COURSE_NOT_PUBLISHED", "This course is not open for enrolment")
        }

        val existing = enrollments.findByStudentIdAndCourseId(studentId, courseId).orElse(null)
        if (existing != null) {
            if (existing.status == EnrollmentStatus.ACTIVE || existing.status == EnrollmentStatus.COMPLETED) {
                throw ConflictException("ALREADY_ENROLLED", "You are already enrolled in this course")
            }
            // Re-enrolling resumes the original record so past progress survives,
            // and starts a fresh access window. Without the new window an
            // enrolment that lapsed would come back already expired -
            // `isClosed()` reads `expiresAt` whatever the status says - so
            // re-enrolling would appear to succeed and grant nothing.
            existing.reactivate(expiryFor(courseId))
            return existing
        }

        return enrollments.save(
            Enrollment(studentId = studentId, courseId = courseId)
                .apply { expiresAt = expiryFor(courseId) },
        )
    }

    /**
     * When this student's access runs out, or null if it does not.
     *
     * Computed from the course's duration **at enrolment** and then stored.
     * Reading it live instead would mean shortening a course's window
     * retroactively cut short access people already held, and lengthening it
     * quietly reopened deals that had closed.
     */
    private fun expiryFor(courseId: UUID, now: Instant = Instant.now()): Instant? =
        catalog.accessDurationDays(courseId)
            ?.takeIf { it > 0 }
            ?.let { now.plus(it.toLong(), ChronoUnit.DAYS) }

    @Transactional(readOnly = true)
    fun listForStudent(studentId: UUID, status: EnrollmentStatus?, pageable: Pageable): Page<Enrollment> =
        if (status == null) {
            enrollments.findByStudentId(studentId, pageable)
        } else {
            enrollments.findByStudentIdAndStatus(studentId, status, pageable)
        }

    @Transactional
    fun cancel(enrollmentId: UUID, studentId: UUID): Enrollment {
        val enrollment = enrollments.findById(enrollmentId)
            .orElseThrow { NotFoundException("ENROLLMENT_NOT_FOUND", "Enrolment not found") }

        // An enrolment belongs to exactly one student; nobody else may touch it.
        if (enrollment.studentId != studentId) {
            throw ForbiddenException("ENROLLMENT_ACCESS_DENIED", "This enrolment is not yours")
        }
        enrollment.cancel()
        return enrollment
    }

    /** Non-throwing check, for callers deciding visibility rather than access. */
    @Transactional(readOnly = true)
    fun hasActiveEnrollment(courseId: UUID, studentId: UUID): Boolean =
        enrollments.findByStudentIdAndCourseId(studentId, courseId)
            .map { !it.isClosed() }
            .orElse(false)

    /** True if the student ever enrolled, whatever the enrolment's state now. */
    @Transactional(readOnly = true)
    fun hasAnyEnrollment(courseId: UUID, studentId: UUID): Boolean =
        enrollments.findByStudentIdAndCourseId(studentId, courseId).isPresent

    @Transactional(readOnly = true)
    fun requireActiveEnrollment(courseId: UUID, studentId: UUID): Enrollment =
        enrollments.findByStudentIdAndCourseId(studentId, courseId)
            .orElseThrow { ForbiddenException("NOT_ENROLLED", "You are not enrolled in this course") }
            .also {
                if (it.isClosed()) {
                    throw ForbiddenException("ENROLLMENT_NOT_ACTIVE", "This enrolment is no longer active")
                }
            }
}
