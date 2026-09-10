package com.elearning.platform.media

import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Direct-to-storage uploads.
 *
 * ```
 * request  -> row written PENDING, presigned PUT returned
 * client   -> PUT bytes straight to storage (never through this app)
 * complete -> size read back from storage, row becomes AVAILABLE
 * ```
 *
 * The size is read from storage rather than trusted from the client, so the
 * recorded metadata cannot disagree with the object that actually exists.
 */
@Service
class MediaService(
    private val media: MediaObjectRepository,
    private val storage: ObjectStorage,
    private val properties: StorageProperties,
) {

    @Transactional
    fun requestUpload(command: RequestUploadCommand, uploaderId: UUID): UploadTicket {
        if (command.contentType.isBlank()) {
            throw BusinessRuleException("MIME_TYPE_REQUIRED", "A content type is required")
        }

        val key = objectKeyFor(command.filename)
        // Public objects live in a separate bucket that allows anonymous reads,
        // so their URLs can be stable and cacheable rather than expiring.
        val bucket = if (command.visibility == MediaVisibility.PUBLIC) {
            properties.publicBucket
        } else {
            properties.mediaBucket
        }
        val record = media.save(
            MediaObject(
                bucket = bucket,
                objectKey = key,
                mimeType = command.contentType,
                originalFilename = command.filename,
                createdBy = uploaderId,
            ),
        )

        val url = storage.presignedUpload(
            bucket = bucket,
            key = key,
            contentType = command.contentType,
            ttl = properties.presignedUrlTtl,
        )
        return UploadTicket(media = record, uploadUrl = url, expiresIn = properties.presignedUrlTtl)
    }

    @Transactional
    fun completeUpload(mediaId: UUID, uploaderId: UUID): MediaObject {
        val record = requireOwned(mediaId, uploaderId)

        val stored = storage.statOf(record.bucket, record.objectKey)
            ?: throw BusinessRuleException("UPLOAD_NOT_FOUND", "No uploaded file was found for this media object")

        // Size and checksum both come from storage, never from the client, so
        // the recorded metadata cannot disagree with the stored object.
        record.markAvailable(stored.sizeBytes, stored.checksum)
        return record
    }

    @Transactional(readOnly = true)
    fun downloadUrl(mediaId: UUID, requesterId: UUID): URI {
        val record = requireOwned(mediaId, requesterId)
        if (!record.isAvailable) {
            throw BusinessRuleException("MEDIA_NOT_AVAILABLE", "This media object has no completed upload")
        }
        return storage.presignedDownload(record.bucket, record.objectKey, properties.presignedUrlTtl)
    }

    /**
     * Stable public URLs for many objects at once.
     *
     * Batched deliberately: a course listing would otherwise issue one query per
     * row to resolve its thumbnail.
     */
    @Transactional(readOnly = true)
    fun publicUrlsFor(mediaIds: Collection<UUID>): Map<UUID, String> {
        if (mediaIds.isEmpty()) return emptyMap()
        return media.findAllById(mediaIds)
            .filter { it.isAvailable && it.bucket == properties.publicBucket }
            .associate { requireNotNull(it.id) to storage.publicUrl(it.bucket, it.objectKey).toString() }
    }

    /**
     * Stores a file the application generated - a rendered certificate, a
     * thumbnail - and returns it already AVAILABLE.
     *
     * `createdBy` stays null: there is no uploader, so the uploader-only access
     * path does not apply and callers must authorize readers themselves.
     */
    /**
     * Stores a generated file at exactly the key given, rather than under a
     * fresh one derived from its name.
     *
     * [storeGenerated] deliberately invents a key: a certificate or a thumbnail
     * is reached only through its media row, and a scattered layout is fine.
     * An HLS rendition is the exception, and it is not a preference. A manifest
     * names its segments by relative name, so a player resolves them against
     * wherever the manifest itself was served from - which means the two have
     * to be siblings in storage, and a key derived from a filename is not.
     *
     * Going through [storeGenerated] here is what put the manifest under
     * `uploads/…/hls-<job>-index.m3u8` while its segments sat under
     * `hls/<job>/`, so every URL the playback service signed pointed at
     * nothing. Presigning does not check that a key exists, so it failed
     * silently and only at playback.
     */
    @Transactional
    fun storeGeneratedAt(objectKey: String, contentType: String, bytes: ByteArray): MediaObject {
        storage.put(properties.mediaBucket, objectKey, bytes, contentType)
        return media.save(
            MediaObject(
                bucket = properties.mediaBucket,
                objectKey = objectKey,
                mimeType = contentType,
                originalFilename = objectKey.substringAfterLast('/'),
                createdBy = null,
            ).apply { markAvailable(bytes.size.toLong()) },
        )
    }

    @Transactional
    fun storeGenerated(filename: String, contentType: String, bytes: ByteArray): MediaObject {
        val key = objectKeyFor(filename)
        storage.put(properties.mediaBucket, key, bytes, contentType)
        return media.save(
            MediaObject(
                bucket = properties.mediaBucket,
                objectKey = key,
                mimeType = contentType,
                originalFilename = filename,
                createdBy = null,
            ).apply { markAvailable(bytes.size.toLong()) },
        )
    }

    /**
     * Looks up an object that is ready to be attached to something.
     *
     * The caller decides whether the attachment is allowed; this only guarantees
     * the object exists and has a completed upload, so a lesson can never point
     * at a half-uploaded file.
     */
    @Transactional(readOnly = true)
    fun requireAvailable(mediaId: UUID): MediaObject {
        val record = media.findById(mediaId)
            .orElseThrow { NotFoundException("MEDIA_NOT_FOUND", "Media object not found") }
        if (!record.isAvailable) {
            throw BusinessRuleException("MEDIA_NOT_AVAILABLE", "This media object has no completed upload")
        }
        return record
    }

    /**
     * A download URL for a caller the *business module* has already authorized -
     * a student reading a lesson in a course they are enrolled in, say.
     *
     * Deliberately skips the uploader check: platform/media provides the
     * capability, and the module that owns the context decides who may use it.
     * Never call this without having authorized the caller first.
     */
    @Transactional(readOnly = true)
    fun downloadUrlForAuthorizedCaller(mediaId: UUID): URI {
        val record = requireAvailable(mediaId)
        return storage.presignedDownload(record.bucket, record.objectKey, properties.presignedUrlTtl)
    }

    /**
     * Only the uploader may act on their own object directly. Access that comes
     * from context (an enrolled student, a course editor) goes through
     * [downloadUrlForAuthorizedCaller] instead.
     */
    private fun requireOwned(mediaId: UUID, userId: UUID): MediaObject {
        val record = media.findById(mediaId)
            .orElseThrow { NotFoundException("MEDIA_NOT_FOUND", "Media object not found") }
        if (record.createdBy != userId) {
            throw ForbiddenException("MEDIA_ACCESS_DENIED", "This media object is not yours")
        }
        return record
    }

    /**
     * Keys are generated, never taken from the client: a caller-supplied key
     * could traverse into another prefix or collide with an existing object.
     * The original filename is preserved in the database, and only a sanitised
     * form appears in the key so downloads keep a sensible name.
     */
    private fun objectKeyFor(filename: String?): String {
        val datePrefix = DATE_FORMAT.format(Instant.now().atZone(ZoneOffset.UTC))
        val safeName = filename
            ?.lowercase(Locale.ROOT)
            ?.replace(UNSAFE_CHARACTERS, "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
            ?.take(MAX_NAME_LENGTH)
            ?: "file"
        return "uploads/$datePrefix/${UUID.randomUUID()}/$safeName"
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM")
        val UNSAFE_CHARACTERS = "[^a-z0-9._-]+".toRegex()
        const val MAX_NAME_LENGTH = 100
    }
}

enum class MediaVisibility { PRIVATE, PUBLIC }

data class RequestUploadCommand(
    val filename: String?,
    val contentType: String,
    val visibility: MediaVisibility = MediaVisibility.PRIVATE,
)

data class UploadTicket(
    val media: MediaObject,
    val uploadUrl: URI,
    val expiresIn: java.time.Duration,
)
