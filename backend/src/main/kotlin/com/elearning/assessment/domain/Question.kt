package com.elearning.assessment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.util.UUID

/** Mirrors `questions.type`. */
enum class QuestionType {
    SINGLE_CHOICE,
    MULTIPLE_CHOICE,
    TRUE_FALSE,
    SHORT_TEXT,
    LONG_TEXT,
    ;

    /** Choice questions can be marked by the machine; free text cannot. */
    val isAutoGradable: Boolean
        get() = this in setOf(SINGLE_CHOICE, MULTIPLE_CHOICE, TRUE_FALSE)
}

@Entity
@Table(name = "questions")
class Question(

    @Column(name = "quiz_id", nullable = false, updatable = false)
    val quizId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: QuestionType,

    @Column(nullable = false)
    var text: String,

    @Column(nullable = false)
    var points: BigDecimal = BigDecimal.ONE,

    @Column(nullable = false)
    var position: Int = 0,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

@Entity
@Table(name = "question_options")
class QuestionOption(

    @Column(name = "question_id", nullable = false, updatable = false)
    val questionId: UUID,

    @Column(nullable = false)
    var text: String,

    /**
     * Never serialised towards a student. Leaking this would hand out the answer
     * key; the student-facing DTO omits it entirely rather than relying on a
     * caller to strip it.
     */
    @Column(name = "is_correct", nullable = false)
    var isCorrect: Boolean = false,

    @Column(nullable = false)
    var position: Int = 0,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
