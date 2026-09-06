package com.elearning.platform.media.application

import com.elearning.platform.media.MediaObject
import com.elearning.platform.media.MediaObjectRepository
import com.elearning.platform.media.MediaReferenceProbe
import com.elearning.platform.media.MediaStatus
import com.elearning.platform.media.ObjectStorage
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Browsing and pruning the asset library.
 *
 * Deletion is the whole of the difficulty. Almost every reference to
 * `media_objects` is `ON DELETE SET NULL`, so the database will happily remove
 * a file a lesson is playing and leave the lesson pointing at nothing - the
 * course does not break loudly, it just stops having a video. So every module
 * holding a reference registers a [MediaReferenceProbe] and the delete is
 * refused while any of them says yes.
 */
@Service
class MediaLibraryService(
    private val media: MediaObjectRepository,
    private val storage: ObjectStorage,
    private val probes: List<MediaReferenceProbe>,
    private val platformAccess: PlatformAccess,
    private val audit: AuditService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun list(term: String?, status: MediaStatus?, pageable: Pageable): Page<MediaObject> {
        platformAccess.require("media.read")
        return when {
            !term.isNullOrBlank() -> media.searchByFilename(term.trim(), pageable)
            status != null -> media.findByStatus(status, pageable)
            else -> media.findAll(pageable)
        }
    }

    @Transactional
    fun delete(mediaId: UUID) {
        platformAccess.require("media.delete")
        val record = media.findById(mediaId)
            .orElseThrow { NotFoundException("MEDIA_NOT_FOUND", "No such file") }

        val holders = probes.filter { it.isReferenced(mediaId) }
        if (holders.isNotEmpty()) {
            throw BusinessRuleException(
                "MEDIA_IN_USE",
                "Cannot delete: this file is still used as " +
                    holders.joinToString(" and ") { it.describe() },
            )
        }

        // The row goes first and the object second. If the storage call fails
        // the transaction rolls back and both survive, which is recoverable;
        // the other order can leave a row pointing at an object that is gone,
        // which is not.
        audit.record(
            action = "media.deleted",
            summary = "Deleted file ${'$'}{record.originalFilename ?: mediaId}",
            targetType = "MEDIA",
            targetId = mediaId,
            details = mapOf("bucket" to record.bucket, "sizeBytes" to record.sizeBytes),
        )
        media.delete(record)
        runCatching { storage.delete(record.bucket, record.objectKey) }
            .onFailure {
                // Deliberately not fatal: a stranded object costs storage, while
                // failing here would keep a row for a file nobody can reach.
                log.warn("Deleted media row {} but could not remove the object: {}", mediaId, it.message)
            }
    }
}
