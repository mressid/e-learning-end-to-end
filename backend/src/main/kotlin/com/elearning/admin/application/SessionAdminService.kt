package com.elearning.admin.application

import com.elearning.admin.infrastructure.AdminRefreshTokenRepository
import com.elearning.identity.application.RefreshTokenService
import com.elearning.identity.infrastructure.RefreshTokenRepository
import com.elearning.identity.infrastructure.SessionRow
import com.elearning.platform.audit.AuditService
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Who is currently signed in, and ending it.
 *
 * This is the part of the dashboard's Security section that has real data
 * behind it. Token *lifetimes* stay in the environment: `JwtProperties` binds
 * once at startup, so a database-backed TTL would need re-reading per request,
 * and a UI control that can extend every session to a year or lock the platform
 * out is a deployment concern wearing a settings hat.
 *
 * A **session is a token family**, not a row. Rotation mints a successor on
 * every refresh, so counting rows would count refreshes and report one login as
 * dozens of sessions.
 */
@Service
class SessionAdminService(
    private val userTokens: RefreshTokenRepository,
    private val adminTokens: AdminRefreshTokenRepository,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val platformAccess: PlatformAccess,
) {

    @Transactional(readOnly = true)
    fun listUserSessions(userId: UUID?, pageable: Pageable): Page<SessionRow> {
        platformAccess.require("settings.manage")
        return userTokens.findLiveSessions(Instant.now(), userId, pageable)
    }

    @Transactional(readOnly = true)
    fun listAdminSessions(adminUserId: UUID?, pageable: Pageable): Page<SessionRow> {
        platformAccess.require("settings.manage")
        return adminTokens.findLiveSessions(Instant.now(), adminUserId, pageable)
    }

    /**
     * Ends one learner session.
     *
     * Revokes the whole family, because the family *is* the session: killing
     * only the newest token would leave its predecessors able to rotate a fresh
     * chain back into existence.
     *
     * The access token already issued stays valid until it expires - a signed
     * JWT cannot be withdrawn - which is why its lifetime is fifteen minutes.
     */
    @Transactional
    fun revokeUserSession(familyId: UUID) {
        platformAccess.require("settings.manage")
        val family = userTokens.findByFamilyId(familyId)
        if (family.isEmpty()) throw NotFoundException("SESSION_NOT_FOUND", "No such session")

        val now = Instant.now()
        family.forEach { it.revoke(now) }
        audit.record(
            action = "session.revoked",
            summary = "Ended a learner session",
            targetType = "USER",
            targetId = family.first().userId,
            details = mapOf("familyId" to familyId.toString(), "tokens" to family.size),
        )
    }

    @Transactional
    fun revokeAdminSession(familyId: UUID) {
        platformAccess.require("settings.manage")
        val family = adminTokens.findByFamilyId(familyId)
        if (family.isEmpty()) throw NotFoundException("SESSION_NOT_FOUND", "No such session")

        val now = Instant.now()
        family.forEach { it.revoke(now) }
        audit.record(
            action = "session.revoked",
            summary = "Ended an administrator session",
            targetType = "ADMIN_USER",
            targetId = family.first().adminUserId,
            details = mapOf("familyId" to familyId.toString(), "tokens" to family.size),
        )
    }

    /** Signs a learner out everywhere, e.g. after a reported compromise. */
    @Transactional
    fun revokeAllUserSessions(userId: UUID) {
        platformAccess.require("settings.manage")
        refreshTokens.revokeAllForUser(userId)
        audit.record(
            action = "session.revoked_all",
            summary = "Signed a learner out of every device",
            targetType = "USER",
            targetId = userId,
        )
    }
}
