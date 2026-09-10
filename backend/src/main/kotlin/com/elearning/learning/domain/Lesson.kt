package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * What a student has to do for a lesson to count as done.
 *
 * Applied by `ProgressService`, not merely recorded. There was a fourth,
 * PERCENTAGE, meaning "a set share of the way through" — with nowhere to say
 * what share. It was dropped rather than given a threshold nobody chose.
 */
enum class CompletionRule {
    /** The student says so. */
    MANUAL,

    /** Opening it is the whole of the requirement. */
    VIEW,

    /** Reaching the end of the runtime the lesson states. */
    DURATION,
}

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
     * Which block a lesson-level request is about, or null to work it out.
     *
     * This used to hold the material, and was the only place a lesson's
     * content could live. The content is now the ordered list of blocks in
     * `item_resources`, and this points at one of them for the two questions
     * that name no block: what `/lesson/content-url` and `/lesson/stream.m3u8`
     * mean, and which runtime DURATION completion is describing.
     *
     * Null means a lesson assembled entirely from blocks, where those
     * questions are answered by taking the first block that can answer them.
     */
    @Column(name = "primary_resource_id")
    var primaryResourceId: UUID? = null,

    @Column
    var description: String? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_rule", nullable = false)
    var completionRule: CompletionRule = CompletionRule.MANUAL,
)
