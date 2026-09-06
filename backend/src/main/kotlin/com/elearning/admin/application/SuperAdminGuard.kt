package com.elearning.admin.application

import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.security.CurrentAdmin
import org.springframework.stereotype.Component

/**
 * Gates the operations that hand out privilege.
 *
 * Deliberately **not** a permission. Anyone who can edit roles or assign them
 * can grant themselves everything, so making it grantable would let a super
 * admin create a role that confers the power to become a super admin - the one
 * escalation the whole scheme exists to prevent.
 */
@Component
class SuperAdminGuard(
    private val permissions: PermissionService,
    private val currentAdmin: CurrentAdmin,
) {

    fun require(): java.util.UUID {
        // A learner's token is authenticated, just not for this audience, so it
        // earns 403 rather than 401 - "who you are is known, and it is not
        // enough". Only a missing or unreadable token is 401.
        val id = currentAdmin.idOrNull() ?: throw denied()
        if (!permissions.isSuperAdmin(id)) throw denied()
        return id
    }

    private fun denied() =
        ForbiddenException("SUPER_ADMIN_ONLY", "Only a super admin may manage roles and administrators")
}
