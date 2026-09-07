package com.elearning.platform.maintenance

import com.elearning.platform.media.MediaStatus
import com.elearning.platform.media.MediaObjectRepository
import com.elearning.platform.media.ObjectStorage
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Resolves uploads that were requested but never confirmed.
 *
 * A row is written PENDING before the presigned URL is handed out, so an
 * abandoned upload leaves a row behind. Two outcomes:
 *
 *  - the object *is* in storage: the client uploaded but never called complete,
 *    so finish the job for them rather than discard a real file
 *  - the object is absent: the upload never happened, mark it FAILED
 *
 * Only rows past the grace period are touched, so an upload still in progress is
 * never swept out from under a client.
 */
@Component
class PendingUploadSweeper(
    private val media: MediaObjectRepository,
    private val storage: ObjectStorage,
    private val properties: MaintenanceProperties,
    private val lock: SchedulerLock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${elearning.maintenance.pending-upload-cron:0 */15 * * * *}")
    @Transactional
    fun scheduledSweep() {
        if (!properties.enabled) return
        // Skipped silently when another instance holds the lock: that instance
        // is doing the work, so this is a normal outcome rather than a problem.
        val result = lock.ifNotRunningElsewhere(LOCK_NAME) { sweep() } ?: return
        if (result.total > 0) {
            log.info("Swept pending uploads: {} recovered, {} failed", result.recovered, result.failed)
        }
    }

    /** Exposed so it can be run on demand and asserted on directly in tests. */
    @Transactional
    fun sweep(now: Instant = Instant.now()): SweepResult {
        val cutoff = now.minus(properties.pendingUploadGrace)
        val stale = media.findByStatusAndCreatedAtBefore(
            MediaStatus.PENDING,
            cutoff,
            PageRequest.of(0, properties.batchSize),
        )

        var recovered = 0
        var failed = 0
        var aborted = 0
        stale.forEach { record ->
            val stored = runCatching { storage.statOf(record.bucket, record.objectKey) }
                .onFailure { log.warn("Could not inspect {}/{}", record.bucket, record.objectKey, it) }
                .getOrNull()

            if (stored != null) {
                record.markAvailable(stored.sizeBytes, stored.checksum)
                recovered++
            } else {
                // A resumable upload that never finished leaves its parts in
                // the bucket. They do not appear in an ordinary listing and are
                // billed, so marking the row FAILED without aborting the upload
                // would lose track of storage that keeps costing money - the
                // same silent growth as an untracked object, which is what this
                // sweep exists to prevent.
                record.uploadId?.let { uploadId ->
                    runCatching { storage.abortMultipartUpload(record.bucket, record.objectKey, uploadId) }
                        .onFailure { log.warn("Could not abort multipart upload {}", uploadId, it) }
                    record.uploadId = null
                    aborted++
                }
                record.markFailed()
                failed++
            }
        }
        return SweepResult(recovered = recovered, failed = failed, abortedUploads = aborted)
    }
}

private const val LOCK_NAME = "pending-upload-sweep"

data class SweepResult(
    val recovered: Int,
    val failed: Int,
    /** Multipart uploads discarded, which is storage that stops being billed. */
    val abortedUploads: Int = 0,
) {
    val total: Int get() = recovered + failed
}
