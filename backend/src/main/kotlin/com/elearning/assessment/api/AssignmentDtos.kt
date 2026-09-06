package com.elearning.assessment.api

import com.elearning.assessment.application.AttemptGradingView
import com.elearning.assessment.domain.Assignment
import com.elearning.assessment.domain.AssignmentSubmission
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(name = "SaveAssignmentRequest")
data class SaveAssignmentRequest(
    val instructions: String? = null,
    @field:DecimalMin("0.01") val maxScore: BigDecimal = BigDecimal("100"),
    val dueAt: Instant? = null,
    val allowLateSubmission: Boolean? = null,
)

@Schema(name = "AssignmentResponse")
data class AssignmentResponse(
    val courseItemId: UUID,
    val instructions: String?,
    val maxScore: BigDecimal,
    val dueAt: Instant?,
    val allowLateSubmission: Boolean,
) {
    companion object {
        fun of(a: Assignment) = AssignmentResponse(
            courseItemId = a.courseItemId,
            instructions = a.instructions,
            maxScore = a.maxScore,
            dueAt = a.dueAt,
            allowLateSubmission = a.allowLateSubmission,
        )
    }
}

@Schema(name = "SubmitAssignmentRequest", description = "Needs text, files, or both")
data class SubmitAssignmentRequest(
    val content: String? = null,
    @get:Schema(description = "Completed uploads belonging to the submitting student")
    val mediaIds: List<UUID> = emptyList(),
)

@Schema(name = "SubmissionResponse")
data class SubmissionResponse(
    val id: UUID,
    val studentId: UUID,
    val attemptNumber: Int,
    val status: String,
    val content: String?,
    val score: BigDecimal?,
    val feedback: String?,
    val submittedAt: Instant?,
    val gradedAt: Instant?,
    val mediaIds: List<UUID>,
) {
    companion object {
        fun of(s: AssignmentSubmission, mediaIds: List<UUID> = emptyList()) = SubmissionResponse(
            id = requireNotNull(s.id),
            studentId = s.studentId,
            attemptNumber = s.attemptNumber,
            status = s.status.name,
            content = s.content,
            score = s.score,
            feedback = s.feedback,
            submittedAt = s.submittedAt,
            gradedAt = s.gradedAt,
            mediaIds = mediaIds,
        )
    }
}

@Schema(name = "GradeSubmissionRequest")
data class GradeSubmissionRequest(
    @field:NotNull @field:DecimalMin("0.0") val score: BigDecimal,
    val feedback: String? = null,
)

@Schema(name = "AttemptGradingResponse", description = "Written answers awaiting a mark")
data class AttemptGradingResponse(
    val attemptId: UUID,
    val status: String,
    val responses: List<ResponseToGradeDto>,
) {
    @Schema(name = "ResponseToGrade")
    data class ResponseToGradeDto(
        val responseId: UUID,
        val questionText: String,
        val maxPoints: BigDecimal,
        val answerText: String?,
        val pointsAwarded: BigDecimal?,
    )

    companion object {
        fun of(v: AttemptGradingView) = AttemptGradingResponse(
            attemptId = requireNotNull(v.attempt.id),
            status = v.attempt.status.name,
            responses = v.responses.map {
                ResponseToGradeDto(it.responseId, it.questionText, it.maxPoints, it.answerText, it.pointsAwarded)
            },
        )
    }
}

@Schema(name = "GradeAttemptRequest")
data class GradeAttemptRequest(
    @field:Valid val grades: List<ResponseGradeRequest> = emptyList(),
)

@Schema(name = "ResponseGradeRequest")
data class ResponseGradeRequest(
    val responseId: UUID,
    @field:DecimalMin("0.0") val pointsAwarded: BigDecimal,
)
