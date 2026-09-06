package com.elearning.community.infrastructure

import com.elearning.community.domain.CourseReview
import com.elearning.community.domain.DiscussionComment
import com.elearning.community.domain.DiscussionThread
import com.elearning.community.domain.ReviewStatus
import com.elearning.community.domain.ThreadStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface DiscussionThreadRepository : JpaRepository<DiscussionThread, UUID> {

    fun findByCourseIdAndStatusNot(courseId: UUID, status: ThreadStatus, pageable: Pageable): Page<DiscussionThread>

    fun findByCourseIdAndLessonIdAndStatusNot(
        courseId: UUID,
        lessonId: UUID,
        status: ThreadStatus,
        pageable: Pageable,
    ): Page<DiscussionThread>
}

interface DiscussionCommentRepository : JpaRepository<DiscussionComment, UUID> {

    fun findByThreadIdOrderByCreatedAt(threadId: UUID): List<DiscussionComment>
}

interface CourseReviewRepository : JpaRepository<CourseReview, UUID> {

    fun findByCourseIdAndStudentId(courseId: UUID, studentId: UUID): Optional<CourseReview>

    fun findByCourseIdAndStatus(courseId: UUID, status: ReviewStatus, pageable: Pageable): Page<CourseReview>

    /**
     * Average and count in one round trip. Returned as an interface projection
     * so the summary endpoint does not load every review just to average them.
     */
    @Query(
        """
        select avg(r.rating) as average, count(r) as total
        from CourseReview r
        where r.courseId = :courseId and r.status = com.elearning.community.domain.ReviewStatus.PUBLISHED
        """,
    )
    fun summarise(@Param("courseId") courseId: UUID): RatingSummaryProjection
}

interface RatingSummaryProjection {
    fun getAverage(): Double?
    fun getTotal(): Long
}
