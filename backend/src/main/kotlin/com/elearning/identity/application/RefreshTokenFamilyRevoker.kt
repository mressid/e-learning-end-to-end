package com.elearning.identity.application

import com.elearning.identity.infrastructure.RefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Revokes a token family in a transaction of its own.
 *
 * This exists as a separate bean for two reasons, both of which have bitten
 * this codebase before:
 *
 * 1. **The rejection must outlive the rollback.** Reuse of a spent refresh
 *    token is answered with a 401, and throwing rolls back the transaction that
 *    threw - taking the revocation with it. The session would then still be
 *    alive, so a thief could simply try again. The kill has to commit even
 *    though the request fails, which is what `REQUIRES_NEW` buys.
 * 2. **A self-call would not get a proxy.** Spring applies `@Transactional`
 *    through a proxy, so calling this from a method of the same class would
 *    silently join the caller's transaction and reintroduce the bug with no
 *    visible sign.
 */
@Service
class RefreshTokenFamilyRevoker(private val tokens: RefreshTokenRepository) {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun revokeFamily(familyId: UUID, now: Instant): Int {
        val family = tokens.findByFamilyId(familyId)
        family.forEach { it.revoke(now) }
        return family.size
    }
}
