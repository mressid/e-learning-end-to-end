package com.elearning.admin.application

import com.elearning.admin.infrastructure.AdminUserRoleRepository
import com.elearning.admin.infrastructure.PermissionRepository
import com.elearning.admin.infrastructure.RolePermissionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * What an administrator is allowed to do.
 *
 * A super role short-circuits: it means *every* permission, including ones a
 * later migration adds. Resolving it by enumerating `role_permissions` instead
 * would silently withhold anything added after the role was created.
 */
@Service
class PermissionService(
    private val adminUserRoles: AdminUserRoleRepository,
    private val permissions: PermissionRepository,
    private val rolePermissionRepository: RolePermissionRepository
) {

    @Transactional(readOnly = true)
    fun isSuperAdmin(adminUserId: UUID): Boolean = adminUserRoles.isSuperAdmin(adminUserId)

    /**
     * Every code this admin holds, for stamping into a token.
     *
     * A super admin gets the catalogue as it stands *at this moment*; because
     * the token is reissued on every refresh, a permission added by a migration
     * reaches them within the access token's lifetime without anyone
     * backfilling a table.
     */
    @Transactional(readOnly = true)
    fun permissionCodesFor(adminUserId: UUID): Set<String> =
        if (isSuperAdmin(adminUserId)) {
            permissions.findAll().map { it.code }.toSet()
        } else {
            adminUserRoles.permissionCodesOf(adminUserId).toSet()
        }

    @Transactional(readOnly = true)
    fun permissionRoles(roleIds: List<UUID>): Set<String> = rolePermissionRepository.permissionRolesOf(roleIds).toSet()

    @Transactional(readOnly = true)
    fun has(adminUserId: UUID, code: String): Boolean =
        isSuperAdmin(adminUserId) || code in adminUserRoles.permissionCodesOf(adminUserId)
}
