package com.elearning.community.api

import com.elearning.community.application.RatingSummary
import com.elearning.community.domain.CourseReview
import com.elearning.community.domain.DiscussionComment
import com.elearning.community.domain.DiscussionThread
import com.elearning.community.domain.ReviewStatus
import com.elearning.community.domain.ThreadStatus
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(name = "CreateThreadRequest")
data class CreateThreadRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val body: String? = null,
    @get:Schema(description = "Attach to one lesson; omit for a course-wide thread")
    val lessonId: UUID? = null,
)

@Schema(name = "ThreadResponse")
data class ThreadResponse(
    val id: UUID,
    val courseId: UUID,
    val lessonId: UUID?,
    val authorId: UUID,
    val title: String,
    val body: String?,
    val status: String,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    companion object {
        fun of(t: DiscussionThread) = ThreadResponse(
            id = requireNotNull(t.id),
            courseId = t.courseId,
            lessonId = t.lessonId,
            authorId = t.authorId,
            title = t.title,
            body = t.body,
            status = t.status.name,
            createdAt = t.createdAt,
            updatedAt = t.updatedAt,
        )
    }
}

@Schema(name = "ThreadDetailResponse")
data class ThreadDetailResponse(
    val thread: ThreadResponse,
    val comments: List<CommentResponse>,
)

@Schema(name = "AddCommentRequest")
data class AddCommentRequest(
    @field:NotBlank val body: String,
    @get:Schema(description = "Reply to another comment in the same thread")
    val parentId: UUID? = null,
)

@Schema(name = "CommentResponse")
data class CommentResponse(
    val id: UUID,
    val authorId: UUID,
    val parentId: UUID?,
    val body: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(c: DiscussionComment) = CommentResponse(
            id = requireNotNull(c.id),
            authorId = c.authorId,
            parentId = c.parentId,
            body = c.body,
            createdAt = c.createdAt,
        )
    }
}

@Schema(name = "ChangeThreadStatusRequest")
data class ChangeThreadStatusRequest(val status: ThreadStatus)

@Schema(name = "WriteReviewRequest")
data class WriteReviewRequest(
    @field:Min(1) @field:Max(5) val rating: Short,
    @field:Size(max = 255) val title: String? = null,
    val body: String? = null,
)

@Schema(name = "ReviewResponse")
data class ReviewResponse(
    val id: UUID,
    val courseId: UUID,
    val studentId: UUID,
    val rating: Short,
    val title: String?,
    val body: String?,
    val status: String,
    val createdAt: Instant,
) {
    companion object {
        fun of(r: CourseReview) = ReviewResponse(
            id = requireNotNull(r.id),
            courseId = r.courseId,
            studentId = r.studentId,
            rating = r.rating,
            title = r.title,
            body = r.body,
            status = r.status.name,
            createdAt = r.createdAt,
        )
    }
}

@Schema(name = "ModerateReviewRequest")
data class ModerateReviewRequest(val status: ReviewStatus)

@Schema(name = "RatingSummaryResponse")
data class RatingSummaryResponse(
    val courseId: UUID,
    @get:Schema(description = "Null when nothing has been published yet")
    val average: BigDecimal?,
    val total: Long,
) {
    companion object {
        fun of(s: RatingSummary) = RatingSummaryResponse(s.courseId, s.average, s.total)
    }
}
