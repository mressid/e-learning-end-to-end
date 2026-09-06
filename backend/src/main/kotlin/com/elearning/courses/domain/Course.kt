package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A course and its publication lifecycle.
 *
 * `ownerId` is a plain UUID rather than a `User` association: the courses
 * module must not depend on identity's entities (§8). It stores who owns the
 * course; identity remains the only owner of what a user *is*.
 */
@Entity
@Table(name = "courses")
class Course(

    @Column(name = "owner_id", nullable = false, updatable = false)
    val ownerId: UUID,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false, updatable = false)
    val slug: String,

    @Column(name = "short_description")
    var shortDescription: String? = null,

    @Column
    var description: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var level: CourseLevel = CourseLevel.ALL_LEVELS,

    @Column(nullable = false)
    var language: String = "en",
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    /**
     * Days of access a student gets when they enrol, or null for no limit.
     *
     * Read once at enrolment and copied onto the enrolment, never consulted
     * afterwards: shortening a course's window must not retroactively cut short
     * access somebody was already granted, and lengthening it should not
     * silently extend a deal that has already closed.
     */
    @Column(name = "access_duration_days")
    var accessDurationDays: Int? = null

    // Change this through publish()/unpublish()/archive() rather than directly:
    // those carry the rules. It cannot be a private setter because the JPA
    // plugin opens entity classes, and Kotlin forbids that combination.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CourseStatus = CourseStatus.DRAFT

    @Column(name = "thumbnail_media_id")
    var thumbnailMediaId: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "published_at")
    var publishedAt: Instant? = null

    val isPublished: Boolean get() = status == CourseStatus.PUBLISHED

    /**
     * A course may only be published once it has something to teach. Callers
     * pass the current item count; the rule itself belongs here, not in the
     * service (§ domain holds business rules).
     */
    fun publish(itemCount: Int, at: Instant = Instant.now()) {
        require(status != CourseStatus.ARCHIVED) { "An archived course cannot be published" }
        require(itemCount > 0) { "A course needs at least one item before it can be published" }
        status = CourseStatus.PUBLISHED
        publishedAt = publishedAt ?: at
        updatedAt = at
    }

    fun unpublish(at: Instant = Instant.now()) {
        status = CourseStatus.DRAFT
        updatedAt = at
    }

    fun archive(at: Instant = Instant.now()) {
        status = CourseStatus.ARCHIVED
        updatedAt = at
    }

    fun touch(at: Instant = Instant.now()) {
        updatedAt = at
    }
}
