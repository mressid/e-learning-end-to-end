package com.elearning.admin.application

import com.elearning.admin.domain.AdminUserRole
import com.elearning.admin.domain.AdminUserRoleId
import com.elearning.admin.domain.Permission
import com.elearning.admin.domain.Role
import com.elearning.admin.domain.RolePermission
import com.elearning.admin.domain.RolePermissionId
import com.elearning.admin.infrastructure.AdminUserRepository
import com.elearning.admin.infrastructure.AdminUserRoleRepository
import com.elearning.admin.infrastructure.PermissionRepository
import com.elearning.admin.infrastructure.RolePermissionRepository
import com.elearning.admin.infrastructure.RoleRepository
import com.elearning.courses.application.SlugGenerator
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.errors.NotFoundException
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Roles, and who holds them.
 *
 * Every method here is reached only by a super admin - managing roles is the
 * super flag, never a grantable permission, because anyone who can edit a role
 * can grant themselves everything in it. Making it a permission would let a
 * super admin hand out the ability to become a super admin.
 */
@Service
class RoleService(
    private val roles: RoleRepository,
    private val permissions: PermissionRepository,
    private val rolePermissions: RolePermissionRepository,
    private val adminUserRoles: AdminUserRoleRepository,
    private val adminUsers: AdminUserRepository,
    private val audit: AuditService,
    private val refreshTokens: AdminRefreshTokenService,
) {

    @Transactional(readOnly = true)
    fun listPermissions(): List<Permission> = permissions.findAllByOrderByCodeAsc()

    @Transactional(readOnly = true)
    fun listRoles(): List<Role> = roles.findAllByOrderByNameAsc()

    @Transactional(readOnly = true)
    fun permissionsOf(roleId: UUID): List<String> = rolePermissions.permissionCodesOf(roleId).sorted()

    @Transactional
    fun create(name: String, description: String?, codes: List<String>): Role {
        val slug = SlugGenerator.slugify(name, SLUG_MAX).ifBlank { "role" }
        if (roles.existsBySlug(slug)) {
            throw ConflictException("ROLE_EXISTS", "A role named \"$name\" already exists")
        }
        val role = roles.save(Role(name = name, slug = slug, description = description))
        replacePermissions(requireNotNull(role.id), codes)
        audit.record(
            action = "role.created",
            summary = "Created role \"$name\"",
            targetType = "ROLE",
            targetId = role.id,
            details = mapOf("permissions" to codes),
        )
        return role
    }

    @Transactional
    fun setPermissions(roleId: UUID, codes: List<String>): Role {
        val role = roles.findById(roleId).orElseThrow { unknownRole() }
        // A super role is "everything, including future permissions"; writing a
        // fixed list onto it would turn that into "everything as of today".
        if (role.isSuper) {
            throw BusinessRuleException(
                "SUPER_ROLE_IMMUTABLE",
                "The super admin role always holds every permission and cannot be edited",
            )
        }
        replacePermissions(roleId, codes)
        role.updatedAt = Instant.now()
        return role
    }

    @Transactional
    fun delete(roleId: UUID) {
        val role = roles.findById(roleId).orElseThrow { unknownRole() }
        if (role.isSystem) {
            throw BusinessRuleException("SYSTEM_ROLE", "A system role cannot be deleted")
        }
        // Everyone holding it loses it, so their sessions must stop carrying
        // the permissions it granted.
        adminUserRoles.findByIdRoleId(roleId).forEach {
            refreshTokens.revokeAllForAdmin(it.id.adminUserId)
        }
        audit.record(
            action = "role.deleted",
            summary = "Deleted role \"${role.name}\"",
            targetType = "ROLE",
            targetId = roleId,
        )
        roles.delete(role)
    }

    @Transactional
    fun assign(adminUserId: UUID, roleId: UUID, grantedBy: UUID): AdminUserRole {
        if (!adminUsers.existsById(adminUserId)) {
            throw NotFoundException("ADMIN_NOT_FOUND", "No such administrator")
        }
        val role = roles.findById(roleId).orElseThrow { unknownRole() }

        val id = AdminUserRoleId(adminUserId, roleId)
        val existing = adminUserRoles.findById(id).orElse(null)
        if (existing != null) return existing

        // A new grant widens what the admin may do, and their current access
        // token predates it. Ending the session makes the change take effect at
        // the next refresh rather than up to a token lifetime later.
        refreshTokens.revokeAllForAdmin(adminUserId)
        val grant = adminUserRoles.save(AdminUserRole(id, grantedBy))
        audit.record(
            action = "role.assigned",
            summary = "Granted role \"${role.name}\" to an administrator",
            targetType = "ADMIN_USER",
            targetId = adminUserId,
            details = mapOf("roleId" to roleId.toString(), "roleName" to role.name),
        )
        return grant
    }

    @Transactional
    fun revoke(adminUserId: UUID, roleId: UUID) {
        val id = AdminUserRoleId(adminUserId, roleId)
        val grant = adminUserRoles.findById(id).orElse(null) ?: return

        val role = roles.findById(roleId).orElseThrow { unknownRole() }
        // Removing the last super admin leaves nobody able to grant the role
        // back: the platform locks itself out with no recovery short of SQL.
        if (role.isSuper && roles.countSuperAdminGrants() <= 1) {
            throw BusinessRuleException(
                "LAST_SUPER_ADMIN",
                "The last super admin cannot be demoted",
            )
        }
        adminUserRoles.delete(grant)
        // A signed token cannot be withdrawn, so the permissions this role
        // granted would otherwise stay usable for the rest of its lifetime.
        refreshTokens.revokeAllForAdmin(adminUserId)
        audit.record(
            action = "role.revoked",
            summary = "Removed role \"${role.name}\" from an administrator",
            targetType = "ADMIN_USER",
            targetId = adminUserId,
            details = mapOf("roleId" to roleId.toString(), "roleName" to role.name),
        )
    }

    /** Whether demoting or suspending this admin would leave nobody in charge. */
    @Transactional(readOnly = true)
    fun isLastSuperAdmin(adminUserId: UUID): Boolean =
        adminUserRoles.isSuperAdmin(adminUserId) && roles.countSuperAdminGrants() <= 1

    @Transactional(readOnly = true)
    fun rolesOf(adminUserId: UUID): List<Role> =
        adminUserRoles.findByIdAdminUserId(adminUserId)
            .mapNotNull { roles.findById(it.id.roleId).orElse(null) }
            .sortedBy(Role::name)

    private fun replacePermissions(roleId: UUID, codes: List<String>) {
        val wanted = codes.distinct()
        val known = permissions.findAllById(wanted).map { it.code }.toSet()
        // An unknown code would be a permission nothing enforces, so the role
        // would claim an authority it does not actually confer.
        val unknown = wanted.filterNot { it in known }
        if (unknown.isNotEmpty()) {
            throw NotFoundException("PERMISSION_NOT_FOUND", "No such permission: ${unknown.first()}")
        }

        rolePermissions.deleteByIdRoleId(roleId)
        rolePermissions.flush()
        rolePermissions.saveAll(wanted.map { RolePermission(RolePermissionId(roleId, it)) })

        // Everyone holding this role now has a different set of permissions
        // than their live tokens claim.
        adminUserRoles.findByIdRoleId(roleId).forEach {
            refreshTokens.revokeAllForAdmin(it.id.adminUserId)
        }
    }

    private fun unknownRole() = NotFoundException("ROLE_NOT_FOUND", "No such role")

    private companion object {
        const val SLUG_MAX = 80
    }
}
