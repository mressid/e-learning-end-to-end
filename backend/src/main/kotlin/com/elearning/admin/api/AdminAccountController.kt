package com.elearning.admin.api

import com.elearning.admin.application.AdminAccountService
import com.elearning.admin.application.PermissionService
import com.elearning.admin.application.RoleService
import com.elearning.admin.application.SuperAdminGuard
import com.elearning.admin.domain.AdminStatus
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.BusinessRuleException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Administrator accounts, managed by a super admin.
 *
 * There is no public signup: creating an account is how someone becomes an
 * administrator, which is why it sits behind the same guard as roles.
 */
@RestController
@RequestMapping("/api/v1/admin/admins")
@Tag(name = "Administrators", description = "Dashboard accounts")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminAccountController(
    private val accounts: AdminAccountService,
    private val roles: RoleService,
    private val superAdmin: SuperAdminGuard,
    private val permissionService: PermissionService
) {

    @GetMapping
    @Operation(summary = "List administrators")
    fun list(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminResponse> {
        superAdmin.require()
        val results = accounts.list(PageRequest.of(page, size))
        return PageResponse.from(results) { AdminResponse.of(it, roles.rolesOf(requireNotNull(it.id)), emptyList()) }
    }

    @PostMapping
    @Operation(
        summary = "Create an administrator",
        description = "The password is handed over out of band and the new administrator " +
            "changes it themselves. There is no verification email: a colleague creating " +
            "the account already establishes what verification would prove. Pass " +
            "`roleIds` to give them their roles in the same transaction - an unknown " +
            "role id rolls the whole thing back rather than leaving an account that " +
            "exists and can do nothing.",
    )
    fun create(@Valid @RequestBody request: CreateAdminRequest): ResponseEntity<AdminResponse> {
        val grantedBy = superAdmin.require()
        val admin = accounts.create(
            request.email,
            request.username,
            request.password,
            request.roleIds,
            grantedBy,
        )
        val assigned = roles.rolesOf(requireNotNull(admin.id))
        val permissions = permissionService.permissionRoles(assigned.mapNotNull { it.id })
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AdminResponse.of(admin, assigned, permissions.toList()))
    }

    @GetMapping("/{adminId}")
    @Operation(summary = "One administrator, with their roles")
    fun get(@PathVariable adminId: UUID): AdminResponse {
        superAdmin.require()
        val roles = roles.rolesOf(adminId)
        val rolesIds = roles.mapNotNull { it.id }
        val permissions = permissionService.permissionRoles(rolesIds)
        return AdminResponse.of(accounts.get(adminId), roles, permissions.toList())
    }

    @PostMapping("/{adminId}/password")
    @Operation(
        summary = "Set an administrator's password",
        description = "For an administrator who has lost theirs. There is no reset-by-email " +
            "on this side and never was, so a super admin sets a new password and hands it " +
            "over the way the first one was handed over. Their sessions end. Refused on your " +
            "own account - use `/admin/auth/me/password`, which asks for the current password " +
            "so that a borrowed session cannot lock you out of your own.",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun setPassword(
        @PathVariable adminId: UUID,
        @Valid @RequestBody request: SetPasswordRequest,
    ) {
        val actor = superAdmin.require()
        accounts.setPassword(adminId, request.newPassword, actor)
    }

    @PostMapping("/{adminId}/status")
    @Operation(
        summary = "Suspend or reinstate an administrator",
        description = "Suspending ends their sessions. Refused for the last super admin.",
    )
    fun setStatus(
        @PathVariable adminId: UUID,
        @Valid @RequestBody request: SetAdminStatusRequest,
    ): AdminResponse {
        superAdmin.require()
        val status = runCatching { AdminStatus.valueOf(request.status) }.getOrElse {
            throw BusinessRuleException("INVALID_STATUS", "Unknown status ${request.status}")
        }
        val roles = roles.rolesOf(adminId)
        val rolesIds = roles.mapNotNull { it.id }
        val permissions = permissionService.permissionRoles(rolesIds)
        return AdminResponse.of(accounts.setStatus(adminId, status), roles, permissions.toList())
    }
}
