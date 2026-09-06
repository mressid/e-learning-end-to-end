package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Proof that a student completed a course.
 *
 * `certificateNumber` is the human-quotable reference; `verificationCode` is the
 * unguessable one a third party uses to check the certificate is genuine. They
 * are separate so the public check cannot be brute-forced from a sequence.
 */
@Entity
@Table(name = "certificates")
class Certificate(

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,

    @Column(name = "certificate_number", nullable = false, updatable = false)
    val certificateNumber: String,

    @Column(name = "verification_code", nullable = false, updatable = false)
    val verificationCode: String,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    /** The rendered PDF, once something renders one. */
    @Column(name = "media_id")
    var mediaId: UUID? = null

    @Column(name = "issued_at", nullable = false, updatable = false)
    val issuedAt: Instant = Instant.now()

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    val isValid: Boolean get() = revokedAt == null

    fun revoke(at: Instant = Instant.now()) {
        revokedAt = revokedAt ?: at
    }
}
