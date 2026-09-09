package com.elearning.platform.media

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** Mirrors `media_objects.status`. */
enum class MediaStatus { PENDING, AVAILABLE, FAILED, DELETED }

/**
 * Metadata for one file in object storage. The bytes live in the bucket; the
 * database only records what the file is and where to find it (§17).
 */
@Entity
@Table(name = "media_objects")
class MediaObject(

    @Column(nullable = false, updatable = false)
    val bucket: String,

    @Column(name = "object_key", nullable = false, updatable = false)
    val objectKey: String,

    @Column(name = "mime_type", nullable = false)
    var mimeType: String,

    @Column(name = "original_filename")
    var originalFilename: String? = null,

    @Column(name = "created_by", updatable = false)
    val createdBy: UUID? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    /**
     * PENDING until the client confirms the upload. A row is written before the
     * presigned URL is handed out, so an abandoned upload leaves a PENDING row
     * that can be swept, rather than an untracked object in the bucket.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: MediaStatus = MediaStatus.PENDING

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0

    @Column
    var checksum: String? = null

    /**
     * The S3 multipart upload in flight, or null for a single-shot upload.
     *
     * Stored because a browser that closed cannot reconstruct it, and without
     * it a half-finished upload is unresumable however many parts storage is
     * still holding. Cleared on completion so a finished object carries no
     * pointer to an upload that no longer exists.
     */
    @Column(name = "upload_id", length = 255)
    var uploadId: String? = null

    /**
     * The streamable version of this file, once transcoding has made one.
     *
     * A rendition belongs to the bytes it was made from, not to whatever refers
     * to them. These lived on the lesson, so two lessons using one video
     * encoded it twice and a video used anywhere else could not be streamed at
     * all. Null until the pipeline finishes, and the original plays meanwhile.
     */
    @Column(name = "hls_manifest_media_id")
    var hlsManifestMediaId: UUID? = null

    /** A frame taken from the video, for the same reason and on the same terms. */
    @Column(name = "poster_media_id")
    var posterMediaId: UUID? = null

    /** Runtime as measured, not as an author described it. */
    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    val isAvailable: Boolean get() = status == MediaStatus.AVAILABLE

    fun markAvailable(sizeBytes: Long, checksum: String? = null) {
        this.sizeBytes = sizeBytes
        checksum?.let { this.checksum = it }
        status = MediaStatus.AVAILABLE
    }

    fun markFailed() {
        status = MediaStatus.FAILED
    }
}
