package com.elearning.platform.transcode

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
 * One piece of transcoding work.
 *
 * Separate from `media_objects.status` because they describe different things:
 * a media status says whether an upload arrived, a job has attempts, an error
 * worth reporting, and a life longer than the request that created it.
 */
@Entity
@Table(name = "transcode_jobs")
class TranscodeJob(

    @Column(name = "media_id", nullable = false)
    val mediaId: UUID,

    @Column(name = "lesson_id")
    val lessonId: UUID? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: TranscodeStatus = TranscodeStatus.QUEUED

    @Column(nullable = false)
    var attempts: Int = 0

    @Column(length = 1000)
    var error: String? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "started_at")
    var startedAt: Instant? = null

    @Column(name = "finished_at")
    var finishedAt: Instant? = null

    fun start(at: Instant = Instant.now()) {
        status = TranscodeStatus.RUNNING
        attempts += 1
        startedAt = at
        error = null
    }

    fun succeed(at: Instant = Instant.now()) {
        status = TranscodeStatus.SUCCEEDED
        finishedAt = at
        error = null
    }

    /** The message is truncated rather than dropped: a reason beats a status. */
    fun fail(reason: String?, at: Instant = Instant.now()) {
        status = TranscodeStatus.FAILED
        finishedAt = at
        error = reason?.take(ERROR_MAX)
    }

    private companion object {
        const val ERROR_MAX = 1000
    }
}

enum class TranscodeStatus { QUEUED, RUNNING, SUCCEEDED, FAILED }
