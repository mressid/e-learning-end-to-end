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

    @Transactional
    fun create(email: String, username: String, password: String): AdminUser {
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
            summary = "Created administrator ${'$'}{created.email}",
            targetType = "ADMIN_USER",
            targetId = created.id,
        )
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
            summary = "Changed administrator ${'$'}{admin.email} from ${'$'}previous to ${'$'}status",
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
