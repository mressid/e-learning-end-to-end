package com.elearning.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A token that may be redeemed exactly once, before it expires.
 *
 * Consumed tokens are marked rather than deleted, so a second click on the same
 * link can be answered precisely instead of looking indistinguishable from a
 * forged one.
 */
@MappedSuperclass
abstract class SingleUseToken(

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "token_hash", nullable = false, length = 64)
    val tokenHash: String,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "consumed_at")
    var consumedAt: Instant? = null

    val isConsumed: Boolean get() = consumedAt != null

    fun hasExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)

    fun isRedeemable(now: Instant = Instant.now()): Boolean = !isConsumed && !hasExpired(now)

    /** Idempotent, so a double submit cannot rewrite when it was first used. */
    fun consume(at: Instant = Instant.now()) {
        if (consumedAt == null) consumedAt = at
    }
}

@Entity
@Table(name = "email_verification_tokens")
class EmailVerificationToken(
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
) : SingleUseToken(userId, tokenHash, expiresAt)

@Entity
@Table(name = "password_reset_tokens")
class PasswordResetToken(
    userId: UUID,
    tokenHash: String,
    expiresAt: Instant,
) : SingleUseToken(userId, tokenHash, expiresAt)
