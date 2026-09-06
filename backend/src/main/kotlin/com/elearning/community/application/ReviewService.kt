package com.elearning.community.application

import com.elearning.community.domain.CourseReview
import com.elearning.community.domain.ReviewStatus
import com.elearning.community.infrastructure.CourseReviewRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.security.PlatformAccess
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Course reviews.
 *
 * Only people who actually enrolled may review, so a rating reflects experience
 * of the course rather than an opinion about it from outside.
 */
@Service
class ReviewService(
    private val reviews: CourseReviewRepository,
    private val context: CommunityContext,
    private val platformAccess: PlatformAccess,
) {

    @Transactional
    fun create(courseId: UUID, command: WriteReviewCommand, studentId: UUID): CourseReview {
        if (!context.courseExists(courseId)) {
            throw NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
        // Checked before enrolment: staff are usually not enrolled, so the
        // enrolment check would otherwise answer with a misleading reason.
        if (context.canEditCourse(courseId, studentId)) {
            throw ForbiddenException("CANNOT_REVIEW_OWN_COURSE", "You cannot review a course you teach")
        }
        if (!context.hasStudied(courseId, studentId)) {
            throw ForbiddenException("NOT_ENROLLED", "Only students of this course can review it")
        }
        requireValidRating(command.rating)

        // The database's unique constraint is the real guarantee; this gives a
        // precise error instead of a constraint violation.
        if (reviews.findByCourseIdAndStudentId(courseId, studentId).isPresent) {
            throw ConflictException("ALREADY_REVIEWED", "You have already reviewed this course")
        }

        return reviews.save(
            CourseReview(
                courseId = courseId,
                studentId = studentId,
                rating = command.rating,
                title = command.title,
                body = command.body,
            ),
        )
    }

    @Transactional
    fun update(reviewId: UUID, command: WriteReviewCommand, studentId: UUID): CourseReview {
        val review = requireReview(reviewId)
        if (review.studentId != studentId) {
            throw ForbiddenException("REVIEW_ACCESS_DENIED", "This review is not yours")
        }
        requireValidRating(command.rating)
        review.edit(command.rating, command.title, command.body)
        return review
    }

    /** Public listing: only published reviews, and only for a published course. */
    @Transactional(readOnly = true)
    fun listPublished(courseId: UUID, pageable: Pageable): Page<CourseReview> {
        if (!context.courseExists(courseId)) {
            throw NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
        return reviews.findByCourseIdAndStatus(courseId, ReviewStatus.PUBLISHED, pageable)
    }

    @Transactional(readOnly = true)
    fun summarise(courseId: UUID): RatingSummary {
        if (!context.courseExists(courseId)) {
            throw NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
        val projection = reviews.summarise(courseId)
        val average = projection.getAverage()
            ?.let { BigDecimal(it).setScale(2, RoundingMode.HALF_UP) }
        return RatingSummary(courseId, average, projection.getTotal())
    }

    /** Moderation, for course staff. */
    @Transactional
    fun moderate(reviewId: UUID, status: ReviewStatus, editorId: UUID): CourseReview {
        val review = requireReview(reviewId)
        // Course staff by relationship, or a platform moderator by permission.
        // The permission is checked second and separately from `course.write`,
        // so moderating reviews does not require the run of the course.
        if (!context.canEditCourse(review.courseId, editorId) &&
            !platformAccess.has("review.moderate")
        ) {
            throw ForbiddenException("MODERATION_DENIED", "Only course staff can moderate reviews")
        }
        review.moderate(status)
        return review
    }

    private fun requireValidRating(rating: Short) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw BusinessRuleException("INVALID_RATING", "Rating must be between 1 and 5")
        }
    }

    private fun requireReview(reviewId: UUID): CourseReview = reviews.findById(reviewId)
        .orElseThrow { NotFoundException("REVIEW_NOT_FOUND", "Review not found") }

    private companion object {
        const val MIN_RATING: Short = 1
        const val MAX_RATING: Short = 5
    }
}

data class WriteReviewCommand(val rating: Short, val title: String?, val body: String?)

data class RatingSummary(val courseId: UUID, val average: BigDecimal?, val total: Long)
