package com.elearning.admin.application

import com.elearning.admin.domain.AdminRefreshToken
import com.elearning.admin.infrastructure.AdminRefreshTokenRepository
import com.elearning.identity.application.SecureTokens
import com.elearning.shared.errors.ApiException
import com.elearning.shared.security.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Admin sessions, with the same rotation and reuse detection as the learner
 * side (see `RefreshTokenService` for why each token is single-use).
 */
@Service
class AdminRefreshTokenService(
    private val tokens: AdminRefreshTokenRepository,
    private val familyRevoker: AdminTokenFamilyRevoker,
    private val properties: JwtProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun issue(adminUserId: UUID, now: Instant = Instant.now()): IssuedAdminToken =
        issueInto(adminUserId, UUID.randomUUID(), now)

    @Transactional
    fun rotate(rawToken: String, now: Instant = Instant.now()): RotatedAdminToken {
        val record = tokens.findByTokenHash(SecureTokens.hash(rawToken)).orElseThrow { invalid() }

        if (record.isRevoked) {
            // A spent token presented again means a copy is in circulation, and
            // there is no telling which holder is genuine. The revocation
            // commits in its own transaction so the 401's rollback cannot undo
            // it - an administrator's session is the last one to leave alive.
            val revoked = familyRevoker.revokeFamily(record.familyId, now)
            log.warn(
                "Admin refresh token reuse detected for {}; revoked {} tokens in family {}",
                record.adminUserId,
                revoked,
                record.familyId,
            )
            throw invalid()
        }
        if (record.hasExpired(now)) throw invalid()

        record.revoke(now)
        return RotatedAdminToken(record.adminUserId, issueInto(record.adminUserId, record.familyId, now))
    }

    @Transactional
    fun revokeSession(rawToken: String, now: Instant = Instant.now()) {
        val record = tokens.findByTokenHash(SecureTokens.hash(rawToken)).orElse(null) ?: return
        tokens.findByFamilyId(record.familyId).forEach { it.revoke(now) }
    }

    /** Used whenever a role changes: the live token's claims are now wrong. */
    @Transactional
    fun revokeAllForAdmin(adminUserId: UUID, now: Instant = Instant.now()) {
        tokens.findByAdminUserId(adminUserId).forEach { it.revoke(now) }
    }

    private fun issueInto(adminUserId: UUID, familyId: UUID, now: Instant): IssuedAdminToken {
        val raw = SecureTokens.random()
        val record = tokens.save(
            AdminRefreshToken(
                adminUserId = adminUserId,
                tokenHash = SecureTokens.hash(raw),
                familyId = familyId,
                expiresAt = now.plus(properties.refreshTokenTtl),
            ).apply { issuedAt = now },
        )
        return IssuedAdminToken(raw, record.expiresAt)
    }

    private fun invalid() =
        ApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")
}

data class IssuedAdminToken(val token: String, val expiresAt: Instant)

data class RotatedAdminToken(val adminUserId: UUID, val refreshToken: IssuedAdminToken)
