package com.elearning.platform.media

import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

/**
 * Resumable uploads, for files a single PUT cannot carry.
 *
 * Two problems, not one. A presigned PUT expires after fifteen minutes while a
 * four-gigabyte lecture takes closer to an hour on an ordinary connection, so
 * large uploads fail *deterministically* rather than unluckily - and when one
 * is interrupted there is nowhere for the transferred bytes to live, so the
 * client starts again from zero.
 *
 * Multipart answers both: a part gets its own short-lived URL, and parts that
 * arrived are held by storage between requests. Resuming is then a question
 * rather than a mechanism - ask what landed, send the rest.
 *
 * Bytes still never pass through the application. Parts are presigned exactly
 * the way whole objects already are, so the property that keeps the multipart
 * limit at 25MB while gigabyte files upload fine is preserved.
 */
@Service
class MultipartUploadService(
    private val media: MediaObjectRepository,
    private val storage: ObjectStorage,
    private val properties: StorageProperties,
) {

    /**
     * Opens a resumable upload and hands back a plan for it.
     *
     * The part size scales with the file rather than being fixed. S3 allows at
     * most 10,000 parts, so a constant 5MB would cap an upload at 50GB - and a
     * constant 100MB would make a 30MB file a pointless three-round-trip dance.
     */
    @Transactional
    fun begin(command: BeginUploadCommand, uploaderId: UUID): MultipartTicket {
        if (command.contentType.isBlank()) {
            throw BusinessRuleException("MIME_TYPE_REQUIRED", "A content type is required")
        }
        if (command.sizeBytes <= 0) {
            // The size decides the part plan, so it has to be real. It is still
            // not trusted for anything else - the stored size is read back from
            // storage on completion.
            throw BusinessRuleException("SIZE_REQUIRED", "A resumable upload needs the file size")
        }

        val bucket = if (command.visibility == MediaVisibility.PUBLIC) {
            properties.publicBucket
        } else {
            properties.mediaBucket
        }
        val key = objectKeyFor(command.filename)
        val uploadId = storage.createMultipartUpload(bucket, key, command.contentType)

        val record = media.save(
            MediaObject(
                bucket = bucket,
                objectKey = key,
                mimeType = command.contentType,
                originalFilename = command.filename,
                createdBy = uploaderId,
            ).apply { this.uploadId = uploadId },
        )

        val partSize = partSizeFor(command.sizeBytes)
        return MultipartTicket(
            media = record,
            uploadId = uploadId,
            partSizeBytes = partSize,
            partCount = ((command.sizeBytes + partSize - 1) / partSize).toInt(),
        )
    }

    /** A URL for one part. Requested as the client reaches it, not all at once. */
    @Transactional(readOnly = true)
    fun urlForPart(mediaId: UUID, partNumber: Int, uploaderId: UUID): URI {
        val record = requireInFlight(mediaId, uploaderId)
        if (partNumber !in 1..MAX_PARTS) {
            throw BusinessRuleException("INVALID_PART_NUMBER", "Part numbers run from 1 to $MAX_PARTS")
        }
        return storage.presignedUploadPart(
            record.bucket,
            record.objectKey,
            requireNotNull(record.uploadId),
            partNumber,
            properties.presignedUrlTtl,
        )
    }

    /**
     * What storage already holds.
     *
     * This is resuming. The client compares its own parts against this and
     * sends the difference, so an upload interrupted at ninety percent costs
     * the last ten rather than all of it.
     */
    @Transactional(readOnly = true)
    fun uploadedParts(mediaId: UUID, uploaderId: UUID): List<UploadedPart> {
        val record = requireInFlight(mediaId, uploaderId)
        return storage.listParts(record.bucket, record.objectKey, requireNotNull(record.uploadId))
    }

    /**
     * Assembles the parts.
     *
     * The ETags come from the client because storage issued them to the client
     * - they are how S3 checks the assembly matches what it actually received.
     * Everything recorded afterwards still comes from storage, not from here.
     */
    @Transactional
    fun complete(mediaId: UUID, parts: List<UploadedPart>, uploaderId: UUID): MediaObject {
        val record = requireInFlight(mediaId, uploaderId)
        if (parts.isEmpty()) {
            throw BusinessRuleException("NO_PARTS", "An upload needs at least one part")
        }

        val uploadId = requireNotNull(record.uploadId)
        storage.completeMultipartUpload(record.bucket, record.objectKey, uploadId, parts)

        val stored = storage.statOf(record.bucket, record.objectKey)
            ?: throw BusinessRuleException("UPLOAD_NOT_FOUND", "The assembled object is not in storage")

        record.markAvailable(stored.sizeBytes, stored.checksum)
        // Cleared, so a finished object carries no pointer to an upload that no
        // longer exists - and so the sweeper cannot mistake it for one in flight.
        record.uploadId = null
        return record
    }

    /**
     * Gives up on an upload and discards its parts.
     *
     * Parts that are neither completed nor aborted stay in the bucket, do not
     * appear in an ordinary listing, and are billed - so abandoning one costs
     * money silently until something clears it.
     */
    @Transactional
    fun abort(mediaId: UUID, uploaderId: UUID) {
        val record = requireInFlight(mediaId, uploaderId)
        storage.abortMultipartUpload(record.bucket, record.objectKey, requireNotNull(record.uploadId))
        record.uploadId = null
        record.status = MediaStatus.FAILED
    }

    private fun requireInFlight(mediaId: UUID, uploaderId: UUID): MediaObject {
        val record = media.findById(mediaId)
            .orElseThrow { NotFoundException("MEDIA_NOT_FOUND", "No such media object") }
        if (record.createdBy != uploaderId) {
            throw ForbiddenException("MEDIA_ACCESS_DENIED", "That upload is not yours")
        }
        if (record.uploadId == null) {
            throw BusinessRuleException(
                "NOT_A_RESUMABLE_UPLOAD",
                "This upload has already finished, or was never resumable",
            )
        }
        return record
    }

    /**
     * Part size, chosen so the part count stays well inside S3's limit.
     *
     * Doubles from the 5MB minimum until 10,000 parts would cover the file,
     * which keeps small files cheap and leaves room for very large ones.
     */
    private fun partSizeFor(sizeBytes: Long): Long {
        var size = MIN_PART_SIZE
        while (sizeBytes / size > MAX_PARTS - 1 && size < MAX_PART_SIZE) size *= 2
        return size
    }

    private fun objectKeyFor(filename: String?): String {
        val safe = filename
            ?.lowercase()
            ?.replace(Regex("[^a-z0-9._-]+"), "-")
            ?.trim('-')
            ?.takeIf { it.isNotBlank() }
            ?.take(80)
            ?: "file"
        return "uploads/${UUID.randomUUID()}/$safe"
    }

    private companion object {
        /** S3's floor for every part but the last. */
        const val MIN_PART_SIZE = 5L * 1024 * 1024
        const val MAX_PART_SIZE = 512L * 1024 * 1024
        const val MAX_PARTS = 10_000
    }
}

data class BeginUploadCommand(
    val filename: String?,
    val contentType: String,
    val sizeBytes: Long,
    val visibility: MediaVisibility = MediaVisibility.PRIVATE,
)

data class MultipartTicket(
    val media: MediaObject,
    val uploadId: String,
    val partSizeBytes: Long,
    val partCount: Int,
)
