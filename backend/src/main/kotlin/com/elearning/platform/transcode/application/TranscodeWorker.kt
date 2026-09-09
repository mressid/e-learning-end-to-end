package com.elearning.platform.transcode.application

import com.elearning.platform.media.MediaService
import com.elearning.platform.media.ObjectStorage
import com.elearning.platform.transcode.TranscodeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Consumes transcoding requests and drives the pipeline.
 *
 * Runs in a process of its own - see `Dockerfile.worker`. Transcoding a lecture
 * pegs a core for minutes and needs scratch space for the source and every
 * rendition, so doing it inside the API would starve request handling, and an
 * FFmpeg crash would take the API down with it.
 *
 * Failure degrades rather than breaks: if this never succeeds,
 * `media_objects.hls_manifest_media_id` stays null and the player falls back to
 * the original upload. The lesson still plays. Same rule certificate rendering follows - a
 * derivative failing must not damage the thing it was derived from.
 */
@Component
class TranscodeWorker(
    private val jobs: TranscodeRepository,
    private val service: TranscodeService,
    private val pipeline: VideoPipeline,
    private val storage: ObjectStorage,
    private val media: MediaService,
    // A separate bean, not a method here: a self-call would not go through the
    // proxy and the result would be written with no transaction at all.
    private val writer: TranscodeResultWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Processes one job.
     *
     * Deliberately not the listener itself. Tests drive this directly, and a
     * `@RabbitListener` active in the test context would consume the same jobs
     * asynchronously underneath the assertions - the async-shared-state trap
     * the maintenance sweeps already avoid by being invoked rather than
     * scheduled.
     */
    fun process(rawJobId: String) {
        val jobId = runCatching { UUID.fromString(rawJobId) }.getOrNull() ?: run {
            // Unparseable: retrying cannot help, so let it dead-letter quietly.
            log.warn("Discarding transcode message with an unreadable id: {}", rawJobId)
            return
        }

        val job = service.markRunning(jobId) ?: run {
            // The job was deleted, or its media was. Nothing to do, and failing
            // would dead-letter a message about work that no longer exists.
            log.warn("Transcode job {} no longer exists", jobId)
            return
        }

        try {
            val source = media.requireAvailable(job.mediaId)
            val bytes = storage.get(source.bucket, source.objectKey)
            val result = pipeline.transcode(bytes, source.originalFilename)

            writer.publish(jobId, job.mediaId, result)
            service.markSucceeded(jobId)
            log.info("Transcoded job {} into {} segments", jobId, result.segments.size)
        } catch (ex: Exception) {
            // Written in its own transaction, because the rethrow below would
            // otherwise roll the record back and leave the job stuck RUNNING
            // with nothing to explain it.
            service.markFailed(jobId, ex.message ?: ex::class.simpleName)
            log.error("Transcode job {} failed", jobId, ex)
            // Rethrown so the broker retries and eventually dead-letters. The
            // listener does not requeue on reject, so a video that reliably
            // kills FFmpeg cannot block every other lesson behind it.
            throw ex
        }
    }
}
