package com.elearning.platform.media

import java.net.URI
import java.time.Duration

/**
 * Object storage, as the rest of the application sees it.
 *
 * Business modules depend on this, never on the S3 SDK (§17). Large files never
 * pass through the application: clients upload and download directly against
 * storage using short-lived presigned URLs, which is why the app's own multipart
 * limit is small.
 */
interface ObjectStorage {

    /** A URL the client may PUT the file to, valid for [ttl]. */
    fun presignedUpload(bucket: String, key: String, contentType: String, ttl: Duration): URI

    /** A URL the client may GET the file from, valid for [ttl]. */
    fun presignedDownload(bucket: String, key: String, ttl: Duration): URI

    /**
     * Uploads bytes the application generated itself, such as a rendered
     * certificate. Presigned URLs are for content that comes from a client;
     * there is no client here.
     */
    fun put(bucket: String, key: String, bytes: ByteArray, contentType: String)

    /**
     * A stable, unsigned URL for an object in a publicly readable bucket.
     *
     * Presigned URLs expire, which makes them useless for something a browser
     * should cache - a course thumbnail on a listing page, say.
     */
    fun publicUrl(bucket: String, key: String): URI

    // ---- resumable uploads ----------------------------------------------
    //
    // Large files arrive in parts. Storage holds the parts between requests,
    // which is the whole reason a resumable upload can resume: the client asks
    // what already landed and sends only the difference. A single PUT has
    // nowhere to keep a half-transferred file.

    /** Opens a multipart upload and returns its id. */
    fun createMultipartUpload(bucket: String, key: String, contentType: String): String

    /**
     * A URL the client may PUT one part to.
     *
     * Per part rather than per file, so the 15-minute window that is far too
     * short for four gigabytes is comfortable for ten megabytes.
     */
    fun presignedUploadPart(
        bucket: String,
        key: String,
        uploadId: String,
        partNumber: Int,
        ttl: Duration,
    ): URI

    /** Parts already stored, which is how a client learns where to resume. */
    fun listParts(bucket: String, key: String, uploadId: String): List<UploadedPart>

    /** Assembles the parts into the final object. */
    fun completeMultipartUpload(bucket: String, key: String, uploadId: String, parts: List<UploadedPart>)

    /**
     * Discards an unfinished upload.
     *
     * Parts that are never completed or aborted stay in the bucket, do not
     * appear in an ordinary listing, and are billed - so abandoning one costs
     * money silently until something cleans it up.
     */
    fun abortMultipartUpload(bucket: String, key: String, uploadId: String)

    /**
     * Reads an object into memory.
     *
     * The exception to "large files never pass through the application": the
     * transcoding worker has to hand real bytes to FFmpeg, and it is a separate
     * process from the API precisely so that this does not happen inside a
     * request. Nothing serving HTTP should call this.
     */
    fun get(bucket: String, key: String): ByteArray

    /** Size and checksum, or null when the object is not present. */
    fun statOf(bucket: String, key: String): StoredObject?

    fun delete(bucket: String, key: String)
}

/**
 * What storage knows about an object.
 *
 * `checksum` is the ETag. For a single-part upload that is the MD5 of the
 * content; for a multipart upload S3 returns a composite value instead, so it
 * is an integrity marker to compare against later, not a content hash to
 * recompute independently.
 */
data class StoredObject(val sizeBytes: Long, val checksum: String?)

/**
 * One part of a multipart upload.
 *
 * The ETag comes from storage when the part is stored and has to be handed back
 * verbatim on completion: it is how S3 verifies the client is assembling the
 * parts it actually received, rather than a list it invented.
 */
data class UploadedPart(val partNumber: Int, val etag: String, val sizeBytes: Long = 0)
