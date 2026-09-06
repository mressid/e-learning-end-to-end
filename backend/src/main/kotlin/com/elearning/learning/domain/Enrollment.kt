package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Mirrors `enrollments.status`. */
enum class EnrollmentStatus { ACTIVE, COMPLETED, CANCELLED, EXPIRED, SUSPENDED }

/**
 * A student's participation in a course.
 *
 * Holds ids rather than associations to `User` and `Course`: learning reads the
 * course catalogue through a port, it does not own identity or course entities (§8).
 */
@Entity
@Table(name = "enrollments")
class Enrollment(

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: EnrollmentStatus = EnrollmentStatus.ACTIVE

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    val enrolledAt: Instant = Instant.now()

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "expires_at")
    var expiresAt: Instant? = null

    val isActive: Boolean get() = status == EnrollmentStatus.ACTIVE

    val isCompleted: Boolean get() = status == EnrollmentStatus.COMPLETED

    /** True once the student may no longer work through the course. */
    fun isClosed(now: Instant = Instant.now()): Boolean =
        status in setOf(EnrollmentStatus.CANCELLED, EnrollmentStatus.EXPIRED, EnrollmentStatus.SUSPENDED) ||
            (expiresAt?.isBefore(now) ?: false)

    /** First sign of activity; recorded once so it means "when they began". */
    fun markStarted(at: Instant = Instant.now()) {
        if (startedAt == null) startedAt = at
    }

    fun complete(at: Instant = Instant.now()) {
        status = EnrollmentStatus.COMPLETED
        completedAt = at
        markStarted(at)
    }

    fun cancel() {
        status = EnrollmentStatus.CANCELLED
    }

    /**
     * Re-enrolling resumes the original record and its progress.
     *
     * [expiresAt] is replaced rather than kept, because `isClosed()` consults it
     * whatever the status says: leaving a lapsed date in place would produce an
     * enrolment that reports ACTIVE and refuses every request.
     */
    fun reactivate(newExpiry: Instant? = null) {
        status = EnrollmentStatus.ACTIVE
        completedAt = null
        expiresAt = newExpiry
    }
}
