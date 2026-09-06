package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/** Mirrors `learning_progress.status`. */
enum class ProgressStatus { NOT_STARTED, IN_PROGRESS, COMPLETED }

/**
 * One student's progress through one course item.
 *
 * `courseId` is denormalised alongside `courseItemId` so "how far through this
 * course am I" does not have to join back up through sections on every read.
 */
@Entity
@Table(name = "learning_progress")
class LearningProgress(

    @Column(name = "student_id", nullable = false, updatable = false)
    val studentId: UUID,

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,

    @Column(name = "course_item_id", nullable = false, updatable = false)
    val courseItemId: UUID,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ProgressStatus = ProgressStatus.NOT_STARTED

    @Column(name = "progress_percent", nullable = false)
    var progressPercent: BigDecimal = BigDecimal.ZERO

    @Column(name = "last_position_seconds")
    var lastPositionSeconds: Int? = null

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "completed_at")
    var completedAt: Instant? = null

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    val isCompleted: Boolean get() = status == ProgressStatus.COMPLETED

    /**
     * Progress only ever moves forward: a video that reports position 30s after
     * 120s must not walk the percentage backwards, and re-watching a finished
     * lesson must not un-complete it.
     */
    fun record(
        status: ProgressStatus,
        percent: BigDecimal?,
        positionSeconds: Int?,
        at: Instant = Instant.now(),
    ) {
        if (isCompleted && status != ProgressStatus.COMPLETED) return

        percent?.let { if (it > progressPercent) progressPercent = it }
        positionSeconds?.let { lastPositionSeconds = it }

        if (this.status == ProgressStatus.NOT_STARTED && status != ProgressStatus.NOT_STARTED) {
            startedAt = startedAt ?: at
        }
        this.status = status

        if (status == ProgressStatus.COMPLETED) {
            progressPercent = HUNDRED
            completedAt = completedAt ?: at
        }
        updatedAt = at
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100.00")
    }
}
