package com.elearning.platform.transcode.application

import com.elearning.platform.transcode.TranscodeJob
import com.elearning.platform.transcode.TranscodePublisher
import com.elearning.platform.transcode.TranscodeRepository
import com.elearning.platform.transcode.TranscodeStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Queueing transcoding work, and recording how it went.
 *
 * The write and the publish are separated on purpose. `enqueue` commits the row
 * in its own transaction and returns; the caller publishes afterwards. Doing
 * both inside one transaction is what lost notifications for a while - the
 * worker is fast, won the race against the commit, found no row and discarded
 * the message.
 */
@Service
class TranscodeService(
    private val jobs: TranscodeRepository,
    private val publisher: TranscodePublisher,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Queues a job, or returns the existing one.
     *
     * Idempotent by file: re-saving a lesson with the same video should not
     * transcode it twice, and neither should a second lesson using it. A job
     * that already failed is retried, because the fix for a transient failure
     * is to ask again.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun enqueue(mediaId: UUID): TranscodeJob {
        val existing = jobs.findByMediaId(mediaId).orElse(null)
        if (existing != null) {
            if (existing.status == TranscodeStatus.FAILED) {
                existing.status = TranscodeStatus.QUEUED
                existing.error = null
            }
            return existing
        }
        return jobs.save(TranscodeJob(mediaId = mediaId))
    }

    /** Committed before the worker can possibly read it. */
    fun enqueueAndPublish(mediaId: UUID) {
        val job = enqueue(mediaId)
        val id = requireNotNull(job.id)
        runCatching { publisher.requestTranscode(id) }
            .onFailure {
                // The row survives, so a sweep or a manual retry can still pick
                // it up. Losing the video would be worse than losing the nudge.
                log.warn("Queued transcode job {} but could not publish it: {}", id, it.message)
            }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markRunning(jobId: UUID): TranscodeJob? =
        jobs.findById(jobId).orElse(null)?.also { it.start() }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markSucceeded(jobId: UUID) {
        jobs.findById(jobId).orElse(null)?.succeed()
    }

    /**
     * Records a failure in its own transaction.
     *
     * The worker rethrows afterwards so the broker can retry and eventually
     * dead-letter, and that rethrow would roll back a failure written in the
     * same transaction - leaving a job stuck RUNNING with nothing explaining
     * why. The same shape as refresh-token reuse revocation.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(jobId: UUID, reason: String?) {
        jobs.findById(jobId).orElse(null)?.fail(reason)
    }
}
