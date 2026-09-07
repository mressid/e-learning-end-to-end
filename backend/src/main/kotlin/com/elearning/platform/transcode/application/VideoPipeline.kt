package com.elearning.platform.transcode.application

/**
 * Turning an uploaded video into something streamable.
 *
 * A port for the same reason `ObjectStorage` and `EmailSender` are: transcoding
 * is the one job on this platform that is genuinely cheaper to buy than to run.
 * Mux and Cloudflare Stream do the ladder, the CDN and signed playback for
 * roughly what the machines would cost, and behind this interface switching to
 * one is a class and a property rather than a rewrite.
 *
 * Everything above this - the job table, the queue, the worker, the playback
 * endpoint - is identical whichever implementation is in place.
 */
interface VideoPipeline {

    /**
     * Produces renditions from [source].
     *
     * Takes and returns bytes rather than storage keys, so the implementation
     * needs no opinion about where anything lives - and a fake in tests needs
     * no storage at all.
     */
    fun transcode(source: ByteArray, sourceFilename: String?): TranscodeResult
}

/**
 * What came out.
 *
 * The manifest references its segments **by name only**, never by URL. Signing
 * happens at playback, per viewer, because a URL baked in at transcode time
 * would either expire long before anyone watched or never expire at all.
 */
data class TranscodeResult(
    /** The HLS master playlist, referencing segments by relative name. */
    val manifest: ByteArray,
    val segments: List<Segment>,
    /** A poster frame, or null if one could not be extracted. */
    val poster: ByteArray?,
    val durationSeconds: Int?,
) {
    data class Segment(val name: String, val bytes: ByteArray, val contentType: String) {
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

/** Raised when the source cannot be turned into renditions. */
class TranscodeFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
