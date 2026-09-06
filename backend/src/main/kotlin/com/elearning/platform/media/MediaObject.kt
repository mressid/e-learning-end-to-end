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
