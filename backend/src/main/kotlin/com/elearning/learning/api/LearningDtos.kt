package com.elearning.learning.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.elearning.learning.application.CourseProgressSummary
import com.elearning.learning.domain.Enrollment
import com.elearning.learning.domain.LearningProgress
import com.elearning.learning.domain.ProgressStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(name = "EnrollmentResponse")
data class EnrollmentResponse(
    val id: UUID,
    val courseId: UUID,
    val status: String,
    val enrolledAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun of(e: Enrollment) = EnrollmentResponse(
            id = requireNotNull(e.id),
            courseId = e.courseId,
            status = e.status.name,
            enrolledAt = e.enrolledAt,
            startedAt = e.startedAt,
            completedAt = e.completedAt,
        )
    }
}

@Schema(name = "RecordProgressRequest")
data class RecordProgressRequest(
    val status: ProgressStatus,

    @field:DecimalMin("0.0") @field:DecimalMax("100.0")
    @get:Schema(description = "Never moves backwards; a lower value is ignored")
    val progressPercent: BigDecimal? = null,

    @field:Min(0)
    @get:Schema(description = "Resume position for timed media")
    val lastPositionSeconds: Int? = null,
)

@Schema(name = "ProgressResponse")
data class ProgressResponse(
    val courseItemId: UUID,
    val courseId: UUID,
    val status: String,
    val progressPercent: BigDecimal,
    val lastPositionSeconds: Int?,
    val startedAt: Instant?,
    val completedAt: Instant?,
) {
    companion object {
        fun of(p: LearningProgress) = ProgressResponse(
            courseItemId = p.courseItemId,
            courseId = p.courseId,
            status = p.status.name,
            progressPercent = p.progressPercent,
            lastPositionSeconds = p.lastPositionSeconds,
            startedAt = p.startedAt,
            completedAt = p.completedAt,
        )
    }
}

@Schema(name = "CourseProgressResponse")
data class CourseProgressResponse(
    val courseId: UUID,
    val requiredItems: Int,
    val completedItems: Int,
    val percentComplete: BigDecimal,
    @get:JsonProperty("isComplete") val isComplete: Boolean,
    val items: List<ProgressResponse>,
) {
    companion object {
        fun of(s: CourseProgressSummary) = CourseProgressResponse(
            courseId = s.courseId,
            requiredItems = s.requiredItems,
            completedItems = s.completedItems,
            percentComplete = s.percentComplete,
            isComplete = s.isComplete,
            items = s.items.map(ProgressResponse::of),
        )
    }
}
