package com.elearning.identity.infrastructure

import com.elearning.identity.domain.RefreshToken
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID


/**
 * One login session, rolled up from the rotation chain that represents it.
 *
 * A family is a session: every refresh mints a successor in the same family, so
 * counting rows would count refreshes, not logins.
 */
interface SessionRow {
    val familyId: UUID
    val subjectId: UUID
    val startedAt: Instant
    val lastUsedAt: Instant
    val expiresAt: Instant
    val tokenCount: Long
}

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    /**
     * Live sessions, newest activity first.
     *
     * Live means the family still has a token that is neither revoked nor
     * expired - a spent token is a step in a chain, not a session that ended.
     *
     * The liveness test is a HAVING rather than a WHERE, deliberately. Rotation
     * revokes the old token on every refresh, so filtering rows first would
     * leave exactly one token per family and report every session as never
     * having been refreshed. The whole chain is counted; the clause only
     * decides which chains are still going.
     */
    @Query(
        """
        select t.familyId as familyId, t.userId as subjectId,
               min(t.issuedAt) as startedAt, max(t.issuedAt) as lastUsedAt,
               max(t.expiresAt) as expiresAt, count(t) as tokenCount
        from RefreshToken t
        where :userId is null or t.userId = :userId
        group by t.familyId, t.userId
        having sum(case when t.revokedAt is null and t.expiresAt > :now then 1 else 0 end) > 0
        order by max(t.issuedAt) desc
        """,
        countQuery = """
        select count(distinct t.familyId) from RefreshToken t
        where t.revokedAt is null and t.expiresAt > :now
          and (:userId is null or t.userId = :userId)
        """,
    )
    fun findLiveSessions(
        @Param("now") now: Instant,
        @Param("userId") userId: UUID?,
        pageable: Pageable,
    ): Page<SessionRow>


    fun findByTokenHash(tokenHash: String): Optional<RefreshToken>

    /** Every token in a login session, for revoking the whole chain at once. */
    fun findByFamilyId(familyId: UUID): List<RefreshToken>

    /** Every token a user holds, for "sign out everywhere" and password changes. */
    fun findByUserId(userId: UUID): List<RefreshToken>
}
