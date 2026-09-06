package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** Mirrors `lessons.content_type`. */
enum class LessonContentType { VIDEO, ARTICLE, DOCUMENT, AUDIO, EXTERNAL }

/** Mirrors `lessons.completion_rule`. */
enum class CompletionRule { MANUAL, VIEW, PERCENTAGE, DURATION }

/**
 * The lesson behind a LESSON-type course item.
 *
 * Shares its primary key with `course_items`: a lesson has no identity of its
 * own, it is what a particular item *is*. The content itself lives in a
 * per-type table so a video and an article need not share one wide row.
 */
@Entity
@Table(name = "lessons")
class Lesson(

    @Id
    @Column(name = "course_item_id", nullable = false, updatable = false)
    val courseItemId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    var contentType: LessonContentType,

    @Column
    var description: String? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", nullable = false)
    var completionRule: CompletionRule = CompletionRule.MANUAL,
)
