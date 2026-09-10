package com.elearning.platform.transcode.application

import com.elearning.learning.application.LessonService
import com.elearning.platform.media.MediaObject
import com.elearning.platform.media.MediaService
import com.elearning.platform.media.ObjectStorage
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
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
    private val lessons: LessonService,
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
        val manifest = manifestObjectFor(lessonId)
        val body = String(storage.get(manifest.bucket, manifest.objectKey))

        // A master playlist references variant playlists, and each variant
        // references its own segments. Signing only what is named here was not
        // enough: a player followed a signed link to `v0.m3u8`, read the
        // relative `v0_000.ts` out of it, resolved that against the storage
        // host *without* a signature - the query string does not survive
        // relative resolution - and was refused by the private bucket. HLS
        // playback got exactly one hop before dying.
        //
        // So a variant comes back through this application, where its segments
        // can be signed the same way. Segments themselves still go straight
        // from storage; only the few kilobytes of playlist pass through here.
        return rewrite(body) { name ->
            if (name.endsWith(".m3u8")) {
                "$STREAM_PATH_PREFIX/$lessonId/lesson/stream/$name"
            } else {
                storage.presignedDownload(manifest.bucket, keyFor(manifest.objectKey, name), ttl())
                    .toString()
            }
        }
    }

    /**
     * One variant playlist of [lessonId], with its segments signed.
     *
     * [name] arrives from a URL, so it is checked against what the manifest
     * actually references rather than trusted. Anything else would make this a
     * way to read arbitrary objects out of the media bucket by asking for
     * `../` and a key: the caller is authorised to watch a lesson, not to
     * fetch whatever they can name.
     */
    @Transactional(readOnly = true)
    fun variantFor(lessonId: UUID, name: String): String {
        val manifest = manifestObjectFor(lessonId)
        val master = String(storage.get(manifest.bucket, manifest.objectKey))

        val referenced = master.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        if (name !in referenced) {
            throw NotFoundException("VARIANT_NOT_FOUND", "This video has no such rendition")
        }

        val body = String(storage.get(manifest.bucket, keyFor(manifest.objectKey, name)))
        return rewrite(body) { segment ->
            storage.presignedDownload(manifest.bucket, keyFor(manifest.objectKey, segment), ttl())
                .toString()
        }
    }

    /**
     * The encoded manifest behind a lesson's video, or why there is not one.
     *
     * The rendition hangs off the file rather than off the lesson, so a video
     * used by more than one lesson is encoded once and streams everywhere.
     */
    private fun manifestObjectFor(lessonId: UUID): MediaObject {
        val sourceId = lessons.fileMediaIdOf(lessonId)
            ?: throw BusinessRuleException("LESSON_HAS_NO_VIDEO", "This lesson has no video")
        val manifestId = media.requireAvailable(sourceId).hlsManifestMediaId
            ?: throw BusinessRuleException(
                "STREAM_NOT_READY",
                "This video is still being processed; the original download still works",
            )
        return media.requireAvailable(manifestId)
    }

    /** Every line that is not blank and not a directive is a reference to rewrite. */
    private fun rewrite(playlist: String, replace: (String) -> String): String =
        playlist.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) line else replace(trimmed)
        }

    /**
     * A sibling of the manifest, by name only.
     *
     * `substringBeforeLast` on the manifest's own key rather than anything the
     * caller supplied, so a name carrying `/` or `..` cannot climb out of the
     * prefix the encode wrote into.
     */
    private fun keyFor(manifestKey: String, name: String): String {
        if (name.contains('/') || name.contains("..")) {
            throw NotFoundException("VARIANT_NOT_FOUND", "This video has no such rendition")
        }
        val prefix = manifestKey.substringBeforeLast('/', "")
        return if (prefix.isEmpty()) name else "$prefix/$name"
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

    private companion object {
        /**
         * Relative, so the manifest works behind whatever host or proxy serves
         * the API. A player resolves it against the URL it fetched the master
         * from, which is this application either way.
         */
        const val STREAM_PATH_PREFIX = "/api/v1/items"
    }
}
