package com.elearning.admin.application

import com.elearning.admin.infrastructure.AdminRefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Revokes an admin token family in a transaction of its own.
 *
 * Same reasoning as `RefreshTokenFamilyRevoker`: the request that detects reuse
 * ends in a 401, and throwing rolls back the transaction that threw - taking
 * the revocation with it and leaving the stolen session alive. A separate bean
 * because Spring applies `@Transactional` through a proxy, so a self-call would
 * silently join the caller's transaction and reintroduce the bug.
 */
@Service
class AdminTokenFamilyRevoker(private val tokens: AdminRefreshTokenRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID, now: Instant): Int {
        val family = tokens.findByFamilyId(familyId)
        family.forEach { it.revoke(now) }
        return family.size
    }
}
