package com.elearning.admin.api

import com.elearning.admin.application.AdminAccountService
import com.elearning.admin.application.AdminAuthenticationService
import com.elearning.admin.application.PermissionService
import com.elearning.admin.application.RoleService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.security.CurrentAdmin
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Administrator sign-in.
 *
 * A separate surface from `/api/v1/auth` because administrators are a separate
 * table: the tokens carry `typ=admin` and are rejected everywhere a learner
 * identity is expected, and vice versa.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin authentication", description = "Dashboard sign-in")
class AdminAuthController(
    private val authentication: AdminAuthenticationService,
    private val accounts: AdminAccountService,
    private val roles: RoleService,
    private val currentAdmin: CurrentAdmin,
    private val permissions: PermissionService,
    ) {

    @PostMapping("/login")
    @Operation(summary = "Sign in to the dashboard")
    fun login(@Valid @RequestBody request: AdminLoginRequest): AdminTokenResponse =
        AdminTokenResponse.of(authentication.login(request.email, request.password))

    @PostMapping("/refresh")
    @Operation(
        summary = "Exchange a refresh token for a new pair",
        description = "Single use, like the learner side. The new access token carries " +
            "the administrator's permissions as they stand now, which is how a role " +
            "change takes effect.",
    )
    fun refresh(@Valid @RequestBody request: AdminRefreshRequest): AdminTokenResponse =
        AdminTokenResponse.of(authentication.refresh(request.refreshToken))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End the dashboard session")
    fun logout(@Valid @RequestBody request: AdminRefreshRequest) =
        authentication.logout(request.refreshToken)

    @GetMapping("/me")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "The signed-in administrator, with roles and permissions")
    fun me(): AdminResponse {
        val id = currentAdmin.requireId()
        val adminRoles = roles.rolesOf(id)
        val adminRolesIds = adminRoles.mapNotNull {  it.id }
        val permissionsOfRoles = permissions.permissionRoles(adminRolesIds)
        return AdminResponse.of(accounts.get(id), adminRoles, permissionsOfRoles.toList())
    }

    @PostMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Change your own password",
        description = "Requires the current one, so a borrowed session cannot lock the " +
            "owner out. Every session ends, including this one.",
    )
    fun changePassword(@Valid @RequestBody request: ChangePasswordRequest) =
        accounts.changeOwnPassword(currentAdmin.requireId(), request.currentPassword, request.newPassword)
}
