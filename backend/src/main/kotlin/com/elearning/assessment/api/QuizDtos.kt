package com.elearning.assessment.api

import com.elearning.assessment.application.AttemptResult
import com.elearning.assessment.application.AttemptView
import com.elearning.assessment.application.QuestionWithOptions
import com.elearning.assessment.domain.QuestionType
import com.elearning.assessment.domain.Quiz
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(name = "SaveQuizRequest")
data class SaveQuizRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val instructions: String? = null,
    @field:DecimalMin("0.0") @get:Schema(description = "Percentage needed to pass; omit to accept any score")
    val passingScore: BigDecimal? = null,
    @field:Min(1) @get:Schema(description = "Omit for unlimited attempts")
    val maxAttempts: Int? = null,
    @field:Min(1) val timeLimitSeconds: Int? = null,
    val randomizeQuestions: Boolean? = null,
)

@Schema(name = "QuizResponse")
data class QuizResponseDto(
    val courseItemId: UUID,
    val title: String,
    val instructions: String?,
    val passingScore: BigDecimal?,
    val maxAttempts: Int?,
    val timeLimitSeconds: Int?,
    val randomizeQuestions: Boolean,
) {
    companion object {
        fun of(q: Quiz) = QuizResponseDto(
            courseItemId = q.courseItemId,
            title = q.title,
            instructions = q.instructions,
            passingScore = q.passingScore,
            maxAttempts = q.maxAttempts,
            timeLimitSeconds = q.timeLimitSeconds,
            randomizeQuestions = q.randomizeQuestions,
        )
    }
}

@Schema(name = "AddQuestionRequest")
data class AddQuestionRequest(
    val type: QuestionType,
    @field:NotBlank val text: String,
    @field:DecimalMin("0.0") val points: BigDecimal? = null,
    @get:Schema(description = "Required for choice questions, forbidden otherwise")
    @field:Valid val options: List<OptionRequest> = emptyList(),
)

@Schema(name = "QuestionOptionRequest")
data class OptionRequest(
    @field:NotBlank val text: String,
    val isCorrect: Boolean = false,
)

/** Author-facing: includes the answer key. Never returned to a student. */
@Schema(name = "AuthorQuestionResponse", description = "Includes correct answers; editors only")
data class AuthorQuestionResponse(
    val id: UUID,
    val type: String,
    val text: String,
    val points: BigDecimal,
    val position: Int,
    val options: List<AuthorOption>,
) {
    @Schema(name = "AuthorQuestionOption")
    data class AuthorOption(val id: UUID, val text: String, val isCorrect: Boolean, val position: Int)

    companion object {
        fun of(q: QuestionWithOptions) = AuthorQuestionResponse(
            id = requireNotNull(q.question.id),
            type = q.question.type.name,
            text = q.question.text,
            points = q.question.points,
            position = q.question.position,
            options = q.options.map {
                AuthorOption(requireNotNull(it.id), it.text, it.isCorrect, it.position)
            },
        )
    }
}

/** Student-facing: no answer key, by construction. */
@Schema(name = "AttemptResponse", description = "Correct answers are never included")
data class AttemptResponse(
    val attemptId: UUID,
    val attemptNumber: Int,
    val status: String,
    val startedAt: Instant,
    val timeLimitSeconds: Int?,
    val questions: List<StudentQuestionResponse>,
) {
    @Schema(name = "StudentQuestionResponse")
    data class StudentQuestionResponse(
        val id: UUID,
        val type: String,
        val text: String,
        val points: BigDecimal,
        val options: List<StudentOptionResponse>,
    )

    @Schema(name = "StudentOptionResponse")
    data class StudentOptionResponse(val id: UUID, val text: String)

    companion object {
        fun of(v: AttemptView) = AttemptResponse(
            attemptId = requireNotNull(v.attempt.id),
            attemptNumber = v.attempt.attemptNumber,
            status = v.attempt.status.name,
            startedAt = v.attempt.startedAt,
            timeLimitSeconds = v.quiz.timeLimitSeconds,
            questions = v.questions.map { q ->
                StudentQuestionResponse(
                    id = q.id,
                    type = q.type.name,
                    text = q.text,
                    points = q.points,
                    options = q.options.map { StudentOptionResponse(it.id, it.text) },
                )
            },
        )
    }
}

@Schema(name = "SubmitAttemptRequest")
data class SubmitAttemptRequest(
    @field:Valid val answers: List<AnswerRequest> = emptyList(),
)

@Schema(name = "AnswerRequest")
data class AnswerRequest(
    val questionId: UUID,
    @get:Schema(description = "One id for single-choice, several for multiple-choice")
    val selectedOptionIds: List<UUID> = emptyList(),
    @get:Schema(description = "For SHORT_TEXT and LONG_TEXT questions")
    val answerText: String? = null,
)

@Schema(name = "AttemptResultResponse")
data class AttemptResultResponse(
    val attemptId: UUID,
    val status: String,
    @get:Schema(description = "Null while free-text answers await manual grading")
    val score: BigDecimal?,
    val passed: Boolean?,
    val awaitingManualGrading: Boolean,
) {
    companion object {
        fun of(r: AttemptResult) = AttemptResultResponse(
            attemptId = requireNotNull(r.attempt.id),
            status = r.attempt.status.name,
            score = r.score,
            passed = r.passed,
            awaitingManualGrading = r.awaitingManualGrading,
        )
    }
}
