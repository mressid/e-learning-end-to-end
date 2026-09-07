package com.elearning.platform.transcode.application

import com.elearning.learning.infrastructure.VideoContentRepository
import com.elearning.platform.media.MediaService
import com.elearning.platform.media.ObjectStorage
import com.elearning.platform.media.StorageProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stores the renditions and points the lesson at them.
 *
 * A separate bean from [TranscodeWorker] for the reason the two token-family
 * revokers already document: Spring applies `@Transactional` through a proxy,
 * so calling this from a method of the worker would silently run it with no
 * transaction at all - the writes would be dirty-checked into nothing and the
 * lesson would stay unlinked while the job reported success. Caught by a test
 * that asked for the manifest and got STREAM_NOT_READY.
 */
@Service
class TranscodeResultWriter(
    private val storage: ObjectStorage,
    private val storageProperties: StorageProperties,
    private val media: MediaService,
    private val videos: VideoContentRepository,
) {

    /**
     * Segments go to storage under one prefix and get **no media rows**: there
     * may be hundreds per video, they are reachable only through the manifest,
     * and a row each would bloat the library the dashboard browses for nobody's
     * benefit. The manifest and the poster do get rows, because those are what
     * the rest of the application refers to.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publish(jobId: UUID, lessonId: UUID?, result: TranscodeResult) {
        val bucket = storageProperties.mediaBucket
        val prefix = "hls/$jobId"

        result.segments.forEach { segment ->
            storage.put(bucket, "$prefix/${segment.name}", segment.bytes, segment.contentType)
        }
        val manifest = media.storeGenerated(
            filename = "$prefix/index.m3u8",
            contentType = "application/vnd.apple.mpegurl",
            bytes = result.manifest,
        )
        val poster = result.poster?.let { media.storeGenerated("$prefix/poster.jpg", "image/jpeg", it) }

        if (lessonId == null) return
        val video = videos.findById(lessonId).orElse(null) ?: return
        video.hlsManifestMediaId = manifest.id
        // Only fill a poster the author has not chosen: an extracted frame is a
        // fallback, not an override of somebody's deliberate choice.
        if (video.thumbnailMediaId == null) video.thumbnailMediaId = poster?.id
        if (video.durationSeconds == null) video.durationSeconds = result.durationSeconds
    }
}
