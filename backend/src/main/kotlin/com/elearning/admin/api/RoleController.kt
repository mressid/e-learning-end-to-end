package com.elearning.admin.api

import com.elearning.admin.application.RoleService
import com.elearning.admin.application.SuperAdminGuard
import com.elearning.shared.api.OpenApiConfig
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Roles and the permissions they carry.
 *
 * Every endpoint is super-admin only, enforced by [SuperAdminGuard] rather than
 * by a permission - see that class for why the power to grant privilege cannot
 * itself be granted.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin roles", description = "Roles, permissions and who holds them")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class RoleController(
    private val roles: RoleService,
    private val superAdmin: SuperAdminGuard,
) {

    @GetMapping("/permissions")
    @Operation(
        summary = "The permission catalogue",
        description = "Seeded reference data. There is no endpoint to create one: the " +
            "set is defined by what the application actually checks, so an invented " +
            "code would name an authority nothing enforces.",
    )
    fun permissions(): List<PermissionResponse> {
        superAdmin.require()
        return roles.listPermissions().map(PermissionResponse::of)
    }

    @GetMapping("/roles")
    @Operation(summary = "List roles")
    fun list(): List<RoleResponse> {
        superAdmin.require()
        return roles.listRoles().map { RoleResponse.of(it, roles.permissionsOf(requireNotNull(it.id))) }
    }

    @PostMapping("/roles")
    @Operation(summary = "Create a role")
    fun create(@Valid @RequestBody request: CreateRoleRequest): ResponseEntity<RoleResponse> {
        superAdmin.require()
        val role = roles.create(request.name, request.description, request.permissions)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(RoleResponse.of(role, roles.permissionsOf(requireNotNull(role.id))))
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Operation(
        summary = "Set the permissions a role carries",
        description = "Replaces the whole set. Refused on the super admin role, which " +
            "means every permission including ones added later - writing a fixed list " +
            "onto it would freeze it at today's catalogue.",
    )
    fun setPermissions(
        @PathVariable roleId: UUID,
        @Valid @RequestBody request: SetRolePermissionsRequest,
    ): RoleResponse {
        superAdmin.require()
        val role = roles.setPermissions(roleId, request.permissions)
        return RoleResponse.of(role, roles.permissionsOf(roleId))
    }

    @DeleteMapping("/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a role", description = "System roles cannot be deleted.")
    fun delete(@PathVariable roleId: UUID) {
        superAdmin.require()
        roles.delete(roleId)
    }

    @PostMapping("/admins/{adminId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Give an administrator a role",
        description = "Their existing sessions end, so the new permissions take effect " +
            "at the next refresh rather than a token lifetime later.",
    )
    fun assign(@PathVariable adminId: UUID, @PathVariable roleId: UUID) {
        val actor = superAdmin.require()
        roles.assign(adminId, roleId, actor)
    }

    @DeleteMapping("/admins/{adminId}/roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Take a role away",
        description = "Refused for the last super admin: the platform would be left with " +
            "nobody able to grant the role back.",
    )
    fun revoke(@PathVariable adminId: UUID, @PathVariable roleId: UUID) {
        superAdmin.require()
        roles.revoke(adminId, roleId)
    }
}
