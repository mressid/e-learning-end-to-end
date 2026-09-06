package com.elearning.community.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Mirrors `course_reviews.status`. */
enum class ReviewStatus { PUBLISHED, PENDING, HIDDEN, REMOVED }

/**
 * A student's rating of a course. One per student per course, enforced by a
 * unique constraint in the database.
 */
@Entity
@Table(name = "course_reviews")
class CourseReview(

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(nullable = false)
    var rating: Short,

    @Column
    var title: String? = null,

    @Column
    var body: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ReviewStatus = ReviewStatus.PUBLISHED

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    val isVisible: Boolean get() = status == ReviewStatus.PUBLISHED

    fun edit(rating: Short?, title: String?, body: String?, at: Instant = Instant.now()) {
        rating?.let { this.rating = it }
        title?.let { this.title = it }
        body?.let { this.body = it }
        updatedAt = at
    }

    fun moderate(newStatus: ReviewStatus, at: Instant = Instant.now()) {
        status = newStatus
        updatedAt = at
    }
}
