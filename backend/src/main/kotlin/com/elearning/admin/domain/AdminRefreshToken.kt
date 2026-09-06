package com.elearning.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The admin equivalent of `RefreshToken`, with the same rotation and reuse
 * detection.
 *
 * A second table rather than a polymorphic subject column on the original,
 * because a column that might reference either of two tables cannot carry a
 * foreign key - and losing referential integrity on sessions to save a table
 * is a bad trade.
 */
@Entity
@Table(name = "admin_refresh_tokens")
class AdminRefreshToken(

    @Column(name = "admin_user_id", nullable = false)
    val adminUserId: UUID,

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

    fun revoke(at: Instant = Instant.now()) {
        if (revokedAt == null) revokedAt = at
    }
}
