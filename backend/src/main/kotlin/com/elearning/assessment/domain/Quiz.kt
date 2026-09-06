package com.elearning.assessment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/**
 * The quiz behind a QUIZ-type course item. Shares the item's primary key: like a
 * lesson, a quiz has no identity of its own.
 */
@Entity
@Table(name = "quizzes")
class Quiz(

    @Id
    @Column(name = "course_item_id", nullable = false, updatable = false)
    val courseItemId: UUID,

    @Column(nullable = false)
    var title: String,

    @Column
    var instructions: String? = null,

    /** Percentage needed to pass. Null means any score passes. */
    @Column(name = "passing_score")
    var passingScore: BigDecimal? = null,

    /** Null means unlimited attempts. */
    @Column(name = "max_attempts")
    var maxAttempts: Int? = null,

    @Column(name = "time_limit_seconds")
    var timeLimitSeconds: Int? = null,

    @Column(name = "randomize_questions", nullable = false)
    var randomizeQuestions: Boolean = false,
) {
    fun allowsAnotherAttempt(used: Int): Boolean = maxAttempts?.let { used < it } ?: true

    fun isPassing(score: BigDecimal): Boolean = passingScore?.let { score >= it } ?: true
}
