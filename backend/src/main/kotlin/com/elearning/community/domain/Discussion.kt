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

/** Mirrors `discussion_threads.status`. */
enum class ThreadStatus { OPEN, ANSWERED, CLOSED, HIDDEN }

/**
 * A question or topic raised inside a course.
 *
 * `lessonId` is optional: a thread is either course-wide or attached to one
 * lesson, which is what lets a course page and a lesson page show different lists.
 */
@Entity
@Table(name = "discussion_threads")
class DiscussionThread(

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,

    @Column(name = "author_id", nullable = false, updatable = false)
    val authorId: UUID,

    @Column(nullable = false)
    var title: String,

    @Column(name = "lesson_id", updatable = false)
    val lessonId: UUID? = null,

    @Column
    var body: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ThreadStatus = ThreadStatus.OPEN

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    /** Hidden threads are moderated away; closed ones simply take no more replies. */
    val acceptsComments: Boolean
        get() = status == ThreadStatus.OPEN || status == ThreadStatus.ANSWERED

    fun changeStatus(newStatus: ThreadStatus, at: Instant = Instant.now()) {
        status = newStatus
        updatedAt = at
    }
}

@Entity
@Table(name = "discussion_comments")
class DiscussionComment(

    @Column(name = "thread_id", nullable = false, updatable = false)
    val threadId: UUID,

    @Column(name = "author_id", nullable = false, updatable = false)
    val authorId: UUID,

    @Column(nullable = false)
    var body: String,

    /** Self-referencing, so replies can nest under another comment. */
    @Column(name = "parent_id", updatable = false)
    val parentId: UUID? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
