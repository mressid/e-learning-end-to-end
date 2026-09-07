package com.elearning.admin.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.elearning.admin.application.AdminSession
import com.elearning.admin.domain.AdminUser
import com.elearning.admin.domain.Permission
import com.elearning.admin.domain.Role
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(name = "AdminLoginRequest")
data class AdminLoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

@Schema(name = "AdminRefreshRequest")
data class AdminRefreshRequest(@field:NotBlank val refreshToken: String)

@Schema(name = "AdminTokenResponse")
data class AdminTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val expiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    @get:Schema(description = "What this administrator may do. A super admin holds every code.")
    val permissions: List<String>,
) {
    companion object {
        fun of(session: AdminSession) = AdminTokenResponse(
            accessToken = session.accessToken.token,
            expiresInSeconds = session.accessToken.expiresInSeconds,
            expiresAt = session.accessToken.expiresAt,
            refreshToken = session.refreshToken.token,
            refreshTokenExpiresAt = session.refreshToken.expiresAt,
            permissions = session.permissions,
        )
    }
}

@Schema(name = "CreateAdminRequest")
data class CreateAdminRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 64) val username: String,
    @get:Schema(description = "Handed to the new administrator, who changes it themselves")
    @field:NotBlank @field:Size(min = 12, max = 128) val password: String,
)

@Schema(name = "ChangePasswordRequest")
data class ChangePasswordRequest(
    @field:NotBlank val currentPassword: String,
    @field:NotBlank @field:Size(min = 12, max = 128) val newPassword: String,
)

@Schema(name = "SetAdminStatusRequest")
data class SetAdminStatusRequest(
    @field:NotBlank
    @get:Schema(allowableValues = ["ACTIVE", "SUSPENDED", "DISABLED"])
    val status: String,
)

@Schema(name = "AdminResponse")
data class AdminResponse(
    val id: UUID,
    val email: String,
    val username: String,
    val status: String,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    val roles: List<RoleSummary>,
) {
    companion object {
        fun of(a: AdminUser, roles: List<Role>) = AdminResponse(
            id = requireNotNull(a.id),
            email = a.email,
            username = a.username,
            status = a.status.name,
            createdAt = a.createdAt,
            lastLoginAt = a.lastLoginAt,
            roles = roles.map(RoleSummary::of),
        )
    }
}

@Schema(name = "RoleSummary")
data class RoleSummary(
    val id: UUID,
    val name: String,
    val slug: String,
    @get:JsonProperty("isSuper") val isSuper: Boolean,
) {
    companion object {
        fun of(r: Role) = RoleSummary(requireNotNull(r.id), r.name, r.slug, r.isSuper)
    }
}

@Schema(name = "RoleResponse")
data class RoleResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    val description: String?,
    @get:Schema(description = "True means every permission, including ones added later")
    @get:JsonProperty("isSuper")
    val isSuper: Boolean,
    @get:JsonProperty("isSystem")
    val isSystem: Boolean,
    val permissions: List<String>,
) {
    companion object {
        fun of(r: Role, permissions: List<String>) = RoleResponse(
            id = requireNotNull(r.id),
            name = r.name,
            slug = r.slug,
            description = r.description,
            isSuper = r.isSuper,
            isSystem = r.isSystem,
            permissions = permissions,
        )
    }
}

@Schema(name = "CreateRoleRequest")
data class CreateRoleRequest(
    @field:NotBlank @field:Size(max = 80) val name: String,
    @field:Size(max = 255) val description: String? = null,
    val permissions: List<String> = emptyList(),
)

@Schema(name = "SetRolePermissionsRequest")
data class SetRolePermissionsRequest(val permissions: List<String> = emptyList())

@Schema(name = "PermissionResponse")
data class PermissionResponse(val code: String, val description: String) {
    companion object {
        fun of(p: Permission) = PermissionResponse(p.code, p.description)
    }
}
