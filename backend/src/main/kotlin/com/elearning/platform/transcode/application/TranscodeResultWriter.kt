package com.elearning.platform.transcode.application

import com.elearning.platform.media.MediaObjectRepository
import com.elearning.platform.media.MediaService
import com.elearning.platform.media.ObjectStorage
import com.elearning.platform.media.StorageProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Stores the renditions and points the source file at them.
 *
 * A separate bean from [TranscodeWorker] for the reason the two token-family
 * revokers already document: Spring applies `@Transactional` through a proxy,
 * so calling this from a method of the worker would silently run it with no
 * transaction at all - the writes would be dirty-checked into nothing and the
 * file would stay unlinked while the job reported success. Caught by a test
 * that asked for the manifest and got STREAM_NOT_READY.
 */
@Service
class TranscodeResultWriter(
    private val storage: ObjectStorage,
    private val storageProperties: StorageProperties,
    private val media: MediaService,
    private val mediaObjects: MediaObjectRepository,
) {

    /**
     * Segments go to storage under one prefix and get **no media rows**: there
     * may be hundreds per video, they are reachable only through the manifest,
     * and a row each would bloat the library the dashboard browses for nobody's
     * benefit. The manifest and the poster do get rows, because those are what
     * the rest of the application refers to.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun publish(jobId: UUID, sourceMediaId: UUID, result: TranscodeResult) {
        val bucket = storageProperties.mediaBucket
        val prefix = "hls/$jobId"

        result.segments.forEach { segment ->
            storage.put(bucket, "$prefix/${segment.name}", segment.bytes, segment.contentType)
        }
        // Stored at exactly this key, not under one derived from the name. A
        // manifest names its segments by relative name, so the two have to be
        // siblings; deriving a key put the manifest under `uploads/…` while the
        // segments above sat under `hls/<job>/`, and every signed segment URL
        // pointed at nothing. Presigning does not check a key exists, so that
        // failed silently and only when somebody tried to watch.
        val manifest = media.storeGeneratedAt(
            objectKey = "$prefix/index.m3u8",
            contentType = "application/vnd.apple.mpegurl",
            bytes = result.manifest,
        )
        val poster = result.poster?.let {
            media.storeGeneratedAt("$prefix/poster.jpg", "image/jpeg", it)
        }

        // Written onto the file rather than onto whatever refers to it. One
        // upload used by two lessons is encoded once and both stream it.
        val source = mediaObjects.findById(sourceMediaId).orElse(null) ?: return
        source.hlsManifestMediaId = manifest.id
        // Only fill a poster nobody has chosen: an extracted frame is a
        // fallback, not an override of somebody's deliberate choice.
        if (source.posterMediaId == null) source.posterMediaId = poster?.id
        if (source.durationSeconds == null) source.durationSeconds = result.durationSeconds
    }
}
