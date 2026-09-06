package com.elearning.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One issued refresh token.
 *
 * Holds a SHA-256 of the token, never the token: a dump of this table must not
 * hand out live sessions. Tokens are 256-bit random values, so there is no
 * dictionary to run against the hash and nothing a slow KDF would buy.
 *
 * [familyId] chains a login session together. Refreshing revokes the presented
 * token and issues its successor into the same family, so seeing a *revoked*
 * token presented again means it was replayed - and since a thief and the
 * rightful owner both hold descendants of that family, the family is the unit
 * that has to die.
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,

    @Column(name = "family_id", nullable = false)
    val familyId: UUID,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "issued_at", nullable = false, updatable = false)
    var issuedAt: Instant = Instant.now()

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null

    val isRevoked: Boolean get() = revokedAt != null

    fun hasExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    /** Usable means issued, not yet revoked, and not yet expired. */
    fun isUsable(now: Instant = Instant.now()): Boolean = !isRevoked && !hasExpired(now)

    fun revoke(at: Instant = Instant.now()) {
        // Idempotent: the first revocation is the one that counts, so a replay
        // cannot rewrite when the session actually ended.
        if (revokedAt == null) revokedAt = at
    }
}
