package com.elearning.assessment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Mirrors `quiz_attempts.status`. */
enum class AttemptStatus { IN_PROGRESS, SUBMITTED, GRADED, ABANDONED, EXPIRED }

/**
 * One sitting of a quiz by one student.
 *
 * Attempts are numbered per student so `max_attempts` can be enforced, and the
 * row is created when the student starts - not when they submit - so a walked-away
 * attempt still counts and a time limit has something to run against.
 */
@Entity
@Table(name = "quiz_attempts")
class QuizAttempt(

    @Column(name = "quiz_id", nullable = false, updatable = false)
    val quizId: UUID,

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(name = "attempt_number", nullable = false, updatable = false)
    val attemptNumber: Int,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AttemptStatus = AttemptStatus.IN_PROGRESS

    @Column
    var score: BigDecimal? = null

    @Column(name = "started_at", nullable = false, updatable = false)
    val startedAt: Instant = Instant.now()

    @Column(name = "submitted_at")
    var submittedAt: Instant? = null

    @Column(name = "graded_at")
    var gradedAt: Instant? = null

    val isOpen: Boolean get() = status == AttemptStatus.IN_PROGRESS

    /** Whether the clock has run out, given the quiz's limit. */
    fun hasExpired(timeLimitSeconds: Int?, now: Instant = Instant.now()): Boolean =
        timeLimitSeconds != null && startedAt.plusSeconds(timeLimitSeconds.toLong()).isBefore(now)

    fun expire() {
        status = AttemptStatus.EXPIRED
        submittedAt = submittedAt ?: Instant.now()
    }

    /** Submitted but holding free-text answers a human still has to mark. */
    fun submitAwaitingManualGrading(at: Instant = Instant.now()) {
        status = AttemptStatus.SUBMITTED
        submittedAt = at
    }

    fun grade(score: BigDecimal, at: Instant = Instant.now()) {
        this.score = score
        status = AttemptStatus.GRADED
        submittedAt = submittedAt ?: at
        gradedAt = at
    }
}

@Entity
@Table(name = "quiz_responses")
class QuizResponse(

    @Column(name = "attempt_id", nullable = false, updatable = false)
    val attemptId: UUID,

    @Column(name = "question_id", nullable = false, updatable = false)
    val questionId: UUID,

    @Column(name = "selected_option_id")
    var selectedOptionId: UUID? = null,

    @Column(name = "answer_text")
    var answerText: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    /** Null while a free-text answer is still awaiting a human. */
    @Column(name = "is_correct")
    var isCorrect: Boolean? = null

    @Column(name = "points_awarded")
    var pointsAwarded: BigDecimal? = null
}
