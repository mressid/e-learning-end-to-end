package com.elearning.identity.application

import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The learner directory behind the dashboard's `/students` page.
 *
 * Every method demands a platform permission. Unlike a course, a directory has
 * no relationship to derive authority from - there is no edge between an
 * administrator and "all users" - so a permission is the only honest way to
 * express it (§11).
 */
@Service
class UserDirectoryService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val platformAccess: PlatformAccess,
) {

    @Transactional(readOnly = true)
    fun list(term: String?, status: UserStatus?, pageable: Pageable): Page<User> {
        platformAccess.require("user.read")
        return when {
            !term.isNullOrBlank() -> users.search(term.trim(), pageable)
            status != null -> users.findByStatus(status, pageable)
            else -> users.findAll(pageable)
        }
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): User {
        platformAccess.require("user.read")
        return users.findById(userId).orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }
    }

    /** Profiles for a page of users, in one query rather than one per row. */
    @Transactional(readOnly = true)
    fun profilesFor(userIds: Collection<UUID>): Map<UUID, UserProfile> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            profiles.findAllById(userIds).associateBy { requireNotNull(it.userId) }
        }

    /**
     * Suspends or reinstates a learner.
     *
     * Suspension takes effect at the account's next sign-in or token refresh,
     * because a signed access token cannot be withdrawn - the same property
     * logout already lives with. Their existing sessions are revoked so the
     * window is one access-token lifetime rather than the refresh token's month.
     */
    @Transactional
    fun setStatus(userId: UUID, status: UserStatus): User {
        platformAccess.require("user.suspend")
        val user = users.findById(userId)
            .orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }

        // PENDING means "has not confirmed their address yet" and is reached by
        // registering, not by an administrator deciding it. Allowing it here
        // would let staff push a verified account back into a state only the
        // verification flow is supposed to produce.
        if (status == UserStatus.PENDING) {
            throw BusinessRuleException(
                "INVALID_STATUS",
                "PENDING is set by registration, not by an administrator",
            )
        }

        val previous = user.status
        user.status = status
        user.updatedAt = Instant.now()
        if (status != UserStatus.ACTIVE) refreshTokens.revokeAllForUser(userId)

        audit.record(
            action = "user.status_changed",
            summary = "Changed ${'$'}{user.email} from ${'$'}previous to ${'$'}status",
            targetType = "USER",
            targetId = userId,
            details = mapOf("from" to previous.name, "to" to status.name),
        )
        return user
    }
}
