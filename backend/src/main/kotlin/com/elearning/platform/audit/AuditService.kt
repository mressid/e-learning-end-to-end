package com.elearning.platform.audit

import com.elearning.shared.security.TokenSubject
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Records what administrators do.
 *
 * The actor is read from the security context rather than passed in, so a call
 * site is one line and cannot get the attribution wrong by handing over the id
 * of whatever it happened to be operating on.
 *
 * **Written in the caller's transaction, deliberately.** If the action rolls
 * back it did not happen, so there should be no record of it; and if the record
 * cannot be written the action fails, which is the correct trade for a security
 * log - an unrecorded privilege change is worse than a refused one. That is the
 * opposite of the choice made for notifications, where a failed email must
 * never lose the work it was announcing.
 */
@Service
class AuditService(private val entries: AuditRepository) {

    @Transactional
    fun record(
        action: String,
        summary: String,
        targetType: String? = null,
        targetId: UUID? = null,
        details: Map<String, Any> = emptyMap(),
    ): AuditEntry {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt
        val type = when (jwt?.getClaimAsString(TokenSubject.CLAIM)) {
            TokenSubject.ADMIN -> ActorType.ADMIN
            TokenSubject.USER -> ActorType.USER
            // Sweeps and listeners act with no token at all.
            else -> ActorType.SYSTEM
        }

        return entries.save(
            AuditEntry(
                actorType = type,
                actorId = jwt?.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                actorLabel = jwt?.getClaimAsString("username"),
                action = action,
                targetType = targetType,
                targetId = targetId,
                summary = summary.take(SUMMARY_MAX),
                details = details.toMutableMap(),
                // The same id the response header and the application logs
                // carry, so an entry can be traced back to its request.
                requestId = MDC.get("requestId"),
            ),
        )
    }

    private companion object {
        const val SUMMARY_MAX = 500
    }
}
