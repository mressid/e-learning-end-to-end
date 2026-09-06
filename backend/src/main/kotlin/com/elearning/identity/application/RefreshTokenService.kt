package com.elearning.identity.application

import com.elearning.identity.domain.RefreshToken
import com.elearning.identity.infrastructure.RefreshTokenRepository
import com.elearning.shared.errors.ApiException
import com.elearning.shared.security.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.UUID

/**
 * Issues, rotates and revokes refresh tokens.
 *
 * An access token cannot be withdrawn once signed - the resource server checks
 * a signature, not a database - so it is deliberately short-lived and the
 * refresh token is the thing that carries a revocable session. "Logging out"
 * therefore means revoking here and waiting out the access token's remaining
 * minutes, not pretending a signed JWT can be recalled.
 */
@Service
class RefreshTokenService(
    private val tokens: RefreshTokenRepository,
    private val familyRevoker: RefreshTokenFamilyRevoker,
    private val properties: JwtProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    /** A brand-new session: a new family with one token in it. */
    @Transactional
    fun issue(userId: UUID, now: Instant = Instant.now()): IssuedRefreshToken =
        issueInto(userId, UUID.randomUUID(), now)

    /**
     * Exchanges a refresh token for the next one in its family.
     *
     * Rotation is what makes theft detectable at all: because each token is
     * usable exactly once, a stolen token and the real one cannot both work.
     * Whichever is presented second arrives already revoked, and that is the
     * signal - at which point the whole family is killed, because there is no
     * way to tell which holder is the legitimate one. The victim is logged out
     * and has to sign in again, which is the correct outcome: the alternative
     * is leaving the thief with a working session.
     */
    @Transactional
    fun rotate(rawToken: String, now: Instant = Instant.now()): RotatedRefreshToken {
        val record = tokens.findByTokenHash(hash(rawToken))
            .orElseThrow { invalid() }

        if (record.isRevoked) {
            // Reuse of a spent token. Revoke every descendant, not just this one
            // - and do it in a transaction that commits even though this request
            // is about to fail, or the rollback would undo the revocation and
            // leave the session alive for whoever tries next.
            val revoked = familyRevoker.revokeFamily(record.familyId, now)
            log.warn(
                "Refresh token reuse detected for user {}; revoked {} tokens in family {}",
                record.userId,
                revoked,
                record.familyId,
            )
            throw invalid()
        }
        if (record.hasExpired(now)) throw invalid()

        record.revoke(now)
        val next = issueInto(record.userId, record.familyId, now)
        return RotatedRefreshToken(record.userId, next)
    }

    /** Ends one session. Idempotent, so a repeated logout is not an error. */
    @Transactional
    fun revokeSession(rawToken: String, now: Instant = Instant.now()) {
        val record = tokens.findByTokenHash(hash(rawToken)).orElse(null) ?: return
        tokens.findByFamilyId(record.familyId).forEach { it.revoke(now) }
    }

    /**
     * Ends every session a user has.
     *
     * Used when the password changes: a password reset that left old sessions
     * alive would not lock out whoever prompted the reset.
     */
    @Transactional
    fun revokeAllForUser(userId: UUID, now: Instant = Instant.now()) {
        tokens.findByUserId(userId).forEach { it.revoke(now) }
    }

    private fun issueInto(userId: UUID, familyId: UUID, now: Instant): IssuedRefreshToken {
        val raw = randomToken()
        val record = tokens.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(raw),
                familyId = familyId,
                expiresAt = now.plus(properties.refreshTokenTtl),
            ).apply { issuedAt = now },
        )
        return IssuedRefreshToken(raw, record.expiresAt)
    }

    private fun randomToken(): String {
        // 256 bits, URL-safe: it travels in a JSON body and sometimes a cookie.
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun invalid() =
        ApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")

    companion object {
        /** Hex SHA-256. Shared with the other single-use token services. */
        fun hash(rawToken: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(rawToken.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}

data class IssuedRefreshToken(val token: String, val expiresAt: Instant)

data class RotatedRefreshToken(val userId: UUID, val refreshToken: IssuedRefreshToken)
