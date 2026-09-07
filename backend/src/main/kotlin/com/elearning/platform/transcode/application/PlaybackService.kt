package com.elearning.platform.transcode.application

import com.elearning.learning.infrastructure.VideoContentRepository
import com.elearning.platform.media.MediaService
import com.elearning.platform.media.ObjectStorage
import com.elearning.shared.errors.BusinessRuleException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.util.UUID

/**
 * Serving an HLS manifest to one authorised viewer.
 *
 * This exists because HLS and presigned URLs do not naturally get along. A
 * manifest names its segments, and a browser fetches each one directly from
 * storage - so either the segments are world-readable, or every reference in
 * the manifest has to be signed.
 *
 * The public bucket was the cheap option and is what course thumbnails use. It
 * is the wrong answer here: enrolment gates every other thing a student can
 * reach, and making the most valuable asset on the platform readable by anyone
 * holding a URL would turn that check into a formality.
 *
 * So the manifest is rewritten per request, with each segment presigned for the
 * viewer asking. The bytes still never pass through the application - segments
 * come from storage directly - only the few kilobytes of playlist do.
 */
@Service
class PlaybackService(
    private val videos: VideoContentRepository,
    private val media: MediaService,
    private val storage: ObjectStorage,
    private val properties: PlaybackProperties,
) {

    /**
     * The manifest for [lessonId], signed for whoever is asking.
     *
     * The caller has already been checked for an active enrolment; this does
     * not re-derive that, it renders for someone already allowed in.
     */
    @Transactional(readOnly = true)
    fun manifestFor(lessonId: UUID): String {
        val video = videos.findById(lessonId).orElse(null)
            ?: throw BusinessRuleException("LESSON_HAS_NO_VIDEO", "This lesson has no video")
        val manifestId = video.hlsManifestMediaId
            ?: throw BusinessRuleException(
                "STREAM_NOT_READY",
                "This video is still being processed; the original download still works",
            )

        val manifest = media.requireAvailable(manifestId)
        val body = String(storage.get(manifest.bucket, manifest.objectKey))
        val prefix = manifest.objectKey.substringBeforeLast('/', "")

        // Every line that is not a directive is a relative reference to a
        // segment or a variant playlist, and each becomes a signed absolute URL.
        return body.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                line
            } else {
                storage.presignedDownload(manifest.bucket, "$prefix/$trimmed", ttl()).toString()
            }
        }
    }

    /**
     * Long enough to outlast the lecture, short enough that a copied link dies.
     *
     * The usual presigned TTL is fifteen minutes, which is right for a download
     * that starts immediately. It is wrong here: a player fetches segments
     * across the whole runtime, so a two-hour lecture would stop dead partway
     * through on URLs that had expired behind it.
     */
    private fun ttl(): Duration = properties.urlTtl
}
