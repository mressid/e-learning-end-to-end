package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/** Mirrors `lessons.completion_rule`. */
enum class CompletionRule { MANUAL, VIEW, PERCENTAGE, DURATION }

/**
 * The lesson behind a LESSON-type course item.
 *
 * Shares its primary key with `course_items`: a lesson has no identity of its
 * own, it is what a particular item *is*.
 *
 * What the lesson teaches with is a [Resource], not a column here. A resource
 * already separates what a material is from where it lives, and records the
 * format of text held inline; a `content_type` on the lesson answered both
 * questions with one word and could answer neither for audio or for a link.
 * What is left on this row is what belongs to the lesson rather than to the
 * material: what it is about, how long it takes, when it counts as done.
 */
@Entity
@Table(name = "lessons")
class Lesson(

    @Id
    @Column(name = "course_item_id", nullable = false, updatable = false)
    val courseItemId: UUID,

    /**
     * The material itself. Never null: a lesson with nothing to teach is a
     * course item that has not been written yet, which is the absence of this
     * row rather than a row with an empty one.
     */
    @Column(name = "primary_resource_id", nullable = false)
    var primaryResourceId: UUID,

    @Column
    var description: String? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", nullable = false)
    var completionRule: CompletionRule = CompletionRule.MANUAL,
)
