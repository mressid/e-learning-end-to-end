package com.elearning.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

/**
 * A named bundle of permissions, assembled by a super admin.
 *
 * [isSuper] is a flag rather than "a role that happens to hold every permission
 * row". Enumerating them would mean a permission added by a later migration is
 * silently not granted, and super admins would quietly lose a capability
 * nobody thought to backfill. The flag means *all, including future ones*.
 */
@Entity
@Table(name = "roles")
class Role(

    @Column(nullable = false)
    var name: String,

    @Column(nullable = false)
    var slug: String,

    @Column
    var description: String? = null,

    @Column(name = "is_super", nullable = false)
    val isSuper: Boolean = false,

    /** Seeded roles the platform depends on. Not deletable. */
    @Column(name = "is_system", nullable = false)
    val isSystem: Boolean = false,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}

/**
 * A capability the application actually checks for.
 *
 * Reference data seeded by migration, never created through the API: the set of
 * codes is defined by what the code enforces, so an endpoint to invent one
 * would write a row nothing consults - the same reasoning categories follow.
 */
@Entity
@Table(name = "permissions")
class Permission(
    @Id
    @Column(nullable = false)
    var code: String = "",

    @Column(nullable = false)
    var description: String = "",
)

@Entity
@Table(name = "role_permissions")
class RolePermission(@EmbeddedId val id: RolePermissionId)

@Embeddable
data class RolePermissionId(
    @Column(name = "role_id") val roleId: UUID,
    @Column(name = "permission_code") val permissionCode: String,
) : Serializable

@Entity
@Table(name = "admin_user_roles")
class AdminUserRole(
    @EmbeddedId val id: AdminUserRoleId,

    @Column(name = "granted_by")
    var grantedBy: UUID? = null,
) {
    @Column(name = "granted_at", nullable = false)
    var grantedAt: Instant = Instant.now()
}

@Embeddable
data class AdminUserRoleId(
    @Column(name = "admin_user_id") val adminUserId: UUID,
    @Column(name = "role_id") val roleId: UUID,
) : Serializable
