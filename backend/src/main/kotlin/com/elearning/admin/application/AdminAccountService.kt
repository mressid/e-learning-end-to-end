package com.elearning.admin.application

import com.elearning.admin.domain.AdminStatus
import com.elearning.admin.domain.AdminUser
import com.elearning.admin.infrastructure.AdminUserRepository
import com.elearning.shared.errors.ApiException
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.errors.NotFoundException
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Administrator accounts.
 *
 * There is no self-registration and no email verification: a super admin
 * creates the account and hands the initial password over, which already
 * establishes the two things verification exists to prove - that the address is
 * real and that its owner agreed to have an account.
 */
@Service
class AdminAccountService(
    private val admins: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val roles: RoleService,
    private val refreshTokens: AdminRefreshTokenService,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun list(pageable: Pageable): Page<AdminUser> = admins.findAllByOrderByCreatedAtDesc(pageable)

    @Transactional(readOnly = true)
    fun get(adminUserId: UUID): AdminUser = admins.findById(adminUserId)
        .orElseThrow { NotFoundException("ADMIN_NOT_FOUND", "No such administrator") }

    /**
     * Creates an administrator and, optionally, gives them their roles.
     *
     * One transaction on purpose. Assigning the roles from the controller after
     * the account is saved would leave an administrator who exists but holds
     * nothing behind if any role id turns out to be wrong - and an account with
     * no roles has no permissions at all, so the failure is silent rather than
     * loud. Here an unknown role rolls the whole thing back.
     */
    @Transactional
    fun create(
        email: String,
        username: String,
        password: String,
        roleIds: List<UUID> = emptyList(),
        grantedBy: UUID? = null,
    ): AdminUser {
        if (admins.existsByEmailIgnoreCase(email)) {
            throw ConflictException("EMAIL_ALREADY_REGISTERED", "That email already has an admin account")
        }
        if (admins.existsByUsernameIgnoreCase(username)) {
            throw ConflictException("USERNAME_TAKEN", "That username is already taken")
        }
        val created = admins.save(
            AdminUser(
                email = email.lowercase(),
                username = username,
                passwordHash = requireNotNull(passwordEncoder.encode(password)),
            ),
        )
        audit.record(
            action = "admin.created",
            summary = "Created administrator ${created.email}",
            targetType = "ADMIN_USER",
            targetId = created.id,
        )

        if (roleIds.isNotEmpty()) {
            val id = requireNotNull(created.id)
            // distinct(): sending the same role twice is a client mistake, not a
            // reason to refuse the request.
            roleIds.distinct().forEach { roleId ->
                roles.assign(id, roleId, requireNotNull(grantedBy) {
                    "grantedBy is required when assigning roles"
                })
            }
        }
        return created
    }

    @Transactional
    fun setStatus(adminUserId: UUID, status: AdminStatus): AdminUser {
        val admin = get(adminUserId)
        // Suspending the last super admin locks the platform out of its own
        // administration just as surely as demoting them does.
        if (status != AdminStatus.ACTIVE && roles.isLastSuperAdmin(adminUserId)) {
            throw BusinessRuleException("LAST_SUPER_ADMIN", "The last super admin cannot be suspended")
        }
        val previous = admin.status
        admin.status = status
        admin.updatedAt = Instant.now()
        audit.record(
            action = "admin.status_changed",
            summary = "Changed administrator ${admin.email} from $previous to $status",
            targetType = "ADMIN_USER",
            targetId = adminUserId,
            details = mapOf("from" to previous.name, "to" to status.name),
        )
        // A suspended admin's signed token stays valid until it expires, so the
        // session has to be ended here rather than waited out.
        if (status != AdminStatus.ACTIVE) refreshTokens.revokeAllForAdmin(adminUserId)
        return admin
    }

    /**
     * Setting another administrator's password, because they have lost it.
     *
     * There is no reset-by-email on this side - admin accounts never had one,
     * and an address that no longer reaches anyone is exactly the situation this
     * exists for. A super admin sets a new password and hands it over the same
     * way the first one was handed over.
     *
     * Refuses to act on the caller's own account, which is not squeamishness:
     * [changeOwnPassword] demands the current password specifically so that a
     * borrowed session cannot lock the real owner out, and allowing self-service
     * here would be a door straight around that check.
     *
     * Their sessions end, so a password that was already shared with the wrong
     * person stops being useful the moment it is replaced.
     */
    @Transactional
    fun setPassword(adminUserId: UUID, newPassword: String, actorId: UUID) {
        if (adminUserId == actorId) {
            throw BusinessRuleException(
                "CANNOT_RESET_OWN_PASSWORD",
                "Change your own password through /admin/auth/me/password, which asks for the current one",
            )
        }
        val admin = get(adminUserId)
        admin.passwordHash = requireNotNull(passwordEncoder.encode(newPassword))
        admin.updatedAt = Instant.now()
        refreshTokens.revokeAllForAdmin(adminUserId)
        audit.record(
            action = "admin.password_reset",
            summary = "Reset the password of administrator ${admin.email}",
            targetType = "ADMIN_USER",
            targetId = adminUserId,
        )
    }

    /**
     * Changing your own password, proving you know the current one.
     *
     * Requiring the old password is what stops a borrowed session from locking
     * the real owner out of their account.
     */
    @Transactional
    fun changeOwnPassword(adminUserId: UUID, currentPassword: String, newPassword: String) {
        val admin = get(adminUserId)
        if (!passwordEncoder.matches(currentPassword, admin.passwordHash)) {
            throw ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Current password is incorrect")
        }
        admin.passwordHash = requireNotNull(passwordEncoder.encode(newPassword))
        admin.updatedAt = Instant.now()
        // Every other session dies: a password change is how you evict someone
        // who should not be signed in.
        refreshTokens.revokeAllForAdmin(adminUserId)
    }
}
