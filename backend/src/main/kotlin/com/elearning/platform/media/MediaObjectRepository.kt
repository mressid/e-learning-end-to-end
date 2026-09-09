package com.elearning.platform.media

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Page
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface MediaObjectRepository : JpaRepository<MediaObject, UUID> {

    fun findByStatus(status: MediaStatus, pageable: Pageable): Page<MediaObject>

    /** Filename search for the library; the generated object key is not useful to a person. */
    @Query(
        """
        select m from MediaObject m
        where lower(m.originalFilename) like lower(concat('%', :term, '%'))
        """,
    )
    fun searchByFilename(@Param("term") term: String, pageable: Pageable): Page<MediaObject>


    /**
     * Whether some other file names this one as its rendition.
     *
     * A manifest or a poster is referenced by the media object it was derived
     * from, so media now answers for its own derivatives rather than asking a
     * module that happens to use them.
     */
    fun existsByHlsManifestMediaId(hlsManifestMediaId: UUID): Boolean

    fun existsByPosterMediaId(posterMediaId: UUID): Boolean

    /** Oldest first, bounded by the caller's page size. */
    fun findByStatusAndCreatedAtBefore(
        status: MediaStatus,
        createdAt: Instant,
        pageable: Pageable,
    ): List<MediaObject>
}
