package com.elearning.assessment.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Mirrors `assignment_submissions.status`. */
enum class SubmissionStatus { DRAFT, SUBMITTED, GRADING, GRADED, RETURNED }

/**
 * The assignment behind an ASSIGNMENT-type course item.
 */
@Entity
@Table(name = "assignments")
class Assignment(

    @Id
    @Column(name = "course_item_id", nullable = false, updatable = false)
    val courseItemId: UUID,

    @Column
    var instructions: String? = null,

    @Column(name = "max_score", nullable = false)
    var maxScore: BigDecimal = BigDecimal("100"),

    @Column(name = "due_at")
    var dueAt: Instant? = null,

    @Column(name = "allow_late_submission", nullable = false)
    var allowLateSubmission: Boolean = false,
) {
    /** Late work is refused unless the assignment explicitly permits it. */
    fun acceptsSubmissionAt(now: Instant = Instant.now()): Boolean =
        allowLateSubmission || dueAt == null || !now.isAfter(dueAt)
}

@Entity
@Table(name = "assignment_submissions")
class AssignmentSubmission(

    @Column(name = "assignment_id", nullable = false, updatable = false)
    val assignmentId: UUID,

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(name = "attempt_number", nullable = false, updatable = false)
    val attemptNumber: Int,

    @Column
    var content: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SubmissionStatus = SubmissionStatus.SUBMITTED

    @Column
    var score: BigDecimal? = null

    @Column
    var feedback: String? = null

    @Column(name = "submitted_at")
    var submittedAt: Instant? = Instant.now()

    @Column(name = "graded_at")
    var gradedAt: Instant? = null

    /** Which instructor marked it, so grading is attributable. */
    @Column(name = "graded_by")
    var gradedBy: UUID? = null

    val isGraded: Boolean get() = status == SubmissionStatus.GRADED

    fun grade(score: BigDecimal, feedback: String?, gradedBy: UUID, at: Instant = Instant.now()) {
        this.score = score
        this.feedback = feedback
        this.gradedBy = gradedBy
        gradedAt = at
        status = SubmissionStatus.GRADED
    }
}

/** Files attached to a submission. Composite key, as the data model specifies. */
@Entity
@Table(name = "submission_media")
class SubmissionMedia(
    @EmbeddedId val id: SubmissionMediaId,
)

@Embeddable
data class SubmissionMediaId(
    @Column(name = "submission_id") val submissionId: UUID,
    @Column(name = "media_id") val mediaId: UUID,
) : Serializable
