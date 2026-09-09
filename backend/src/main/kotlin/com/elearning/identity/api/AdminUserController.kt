package com.elearning.identity.api

import com.elearning.identity.application.CreateUserCommand
import com.elearning.identity.application.UpdateUserCommand
import com.elearning.identity.application.UserDirectoryService
import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.domain.UserType
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.ApiError
import com.elearning.shared.errors.BusinessRuleException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.constraints.NotBlank
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "DirectoryUserResponse")
data class DirectoryUserResponse(
    val id: UUID,
    val email: String,
    val username: String,
    val status: String,
    val displayName: String?,
    val createdAt: Instant,
    val lastLoginAt: Instant?,
    @get:Schema(
        description = "Which kind of account this is. Fixed at creation - there is no " +
            "transition between the two.",
        allowableValues = ["STUDENT", "INSTRUCTOR"],
    )
    val type: String,
) {
    companion object {
        fun of(user: User, profile: UserProfile?) = DirectoryUserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            username = user.username,
            status = user.status.name,
            displayName = profile?.displayName,
            createdAt = user.createdAt,
            lastLoginAt = user.lastLoginAt,
            type = user.type.name,
        )
    }
}

@Schema(name = "CreateUserRequest")
data class CreateUserRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 3, max = 50) val username: String,
    @field:NotBlank @field:Size(min = 12, max = 128)
    @get:Schema(description = "A starting password. Nothing forces a change on first sign-in, so hand it over deliberately.")
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    @get:Schema(
        description = "Which kind of account to create. Permanent: there is no edit that " +
            "turns one into the other, so someone who both learns and teaches holds two " +
            "accounts. Only an INSTRUCTOR may own or co-instruct a course.",
        allowableValues = ["STUDENT", "INSTRUCTOR"],
        defaultValue = "STUDENT",
    )
    val type: String = "STUDENT",
)

@Schema(
    name = "UpdateUserRequest",
    description = "Every field optional; only what is sent changes. The kind of account is " +
        "not editable - create the other kind instead.",
)
data class UpdateUserRequest(
    @field:Email val email: String? = null,
    @field:Size(min = 3, max = 50) val username: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Schema(
    name = "SetUserPasswordRequest",
    description = "A replacement password, chosen by the administrator doing the reset and " +
        "handed over out of band. Nothing forces a change on first sign-in.",
)
data class SetUserPasswordRequest(
    @field:NotBlank @field:Size(min = 12, max = 128) val newPassword: String,
)

@Schema(name = "SetUserStatusRequest")
data class SetUserStatusRequest(
    @field:NotBlank
    @get:Schema(allowableValues = ["ACTIVE", "SUSPENDED", "DISABLED"])
    val status: String,
)

/**
 * The learner directory, for the dashboard's `/students` page.
 *
 * Served from `identity` rather than the `admin` module: the data belongs here,
 * and moving it would make `admin` depend on every module that owns something a
 * dashboard renders (§8). The `/api/v1/admin` prefix is a URL namespace, not a
 * statement about which module answers.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users", description = "The learner directory")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminUserController(private val directory: UserDirectoryService) {

    @GetMapping
    @Operation(
        summary = "List or search learners",
        description = "Requires `user.read`. Pass `q` to match an email or username, " +
            "or `status` to filter; `q` wins if both are given.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "A page of users"),
        ApiResponse(
            responseCode = "403",
            description = "The caller lacks user.read",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<DirectoryUserResponse> {
        val parsed = status?.let(::parseStatus)
        val results = directory.list(
            q,
            parsed,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        // One lookup for the page rather than a profile query per row - the
        // same N+1 the course listing already avoids for thumbnails.
        val profiles = directory.profilesFor(results.content.mapNotNull { it.id })
        return PageResponse.from(results) { DirectoryUserResponse.of(it, profiles[it.id]) }
    }

    @GetMapping("/{userId}")
    @Operation(summary = "One learner", description = "Requires `user.read`.")
    fun get(@PathVariable userId: UUID): DirectoryUserResponse {
        val user = directory.get(userId)
        return DirectoryUserResponse.of(user, directory.profilesFor(listOf(userId))[userId])
    }

    @PostMapping
    @Operation(
        summary = "Create an account",
        description = "Requires `user.write`. ACTIVE immediately - an administrator typing " +
            "the address is the verification, so there is no email to wait for. `type` " +
            "decides whether this is a learner or an author, and cannot be changed later.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Created"),
        ApiResponse(
            responseCode = "409",
            description = "That email or username is taken",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun create(@Valid @RequestBody request: CreateUserRequest): DirectoryUserResponse {
        val user = directory.create(
            CreateUserCommand(
                email = request.email,
                username = request.username,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
                type = parseType(request.type),
            ),
        )
        val id = requireNotNull(user.id)
        return DirectoryUserResponse.of(user, directory.profilesFor(listOf(id))[id])
    }

    @PatchMapping("/{userId}")
    @Operation(
        summary = "Edit an account",
        description = "Requires `user.write`. Only the fields you send change. Which kind " +
            "of account this is cannot be edited here, or anywhere: a learner who starts " +
            "teaching is given an instructor account rather than converted into one.",
    )
    fun update(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: UpdateUserRequest,
    ): DirectoryUserResponse {
        val user = directory.update(
            userId,
            UpdateUserCommand(
                email = request.email,
                username = request.username,
                firstName = request.firstName,
                lastName = request.lastName,
            ),
        )
        return DirectoryUserResponse.of(user, directory.profilesFor(listOf(userId))[userId])
    }

    @PostMapping("/{userId}/password")
    @Operation(
        summary = "Set a learner's or instructor's password",
        description = "Requires `user.write`. For when reset-by-email cannot work - an " +
            "instructor whose address was never real, or a learner who no longer has the " +
            "inbox. Self-service reset stays the normal route. Their sessions end, so a " +
            "password that reached the wrong person stops working when it is replaced.",
    )
    fun setPassword(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: SetUserPasswordRequest,
    ): DirectoryUserResponse {
        val user = directory.setPassword(userId, request.newPassword)
        return DirectoryUserResponse.of(user, directory.profilesFor(listOf(userId))[userId])
    }

    @PostMapping("/{userId}/status")
    @Operation(
        summary = "Suspend or reinstate a learner",
        description = "Requires `user.suspend`. Suspending revokes their sessions, so " +
            "access ends within one access-token lifetime rather than lasting the " +
            "refresh token's month.",
    )
    fun setStatus(
        @PathVariable userId: UUID,
        @Valid @RequestBody request: SetUserStatusRequest,
    ): DirectoryUserResponse {
        val user = directory.setStatus(userId, parseStatus(request.status))
        return DirectoryUserResponse.of(user, directory.profilesFor(listOf(userId))[userId])
    }

    private fun parseStatus(raw: String): UserStatus =
        runCatching { UserStatus.valueOf(raw.uppercase()) }
            .getOrElse { throw BusinessRuleException("INVALID_STATUS", "Unknown status $raw") }

    private fun parseType(raw: String): UserType =
        runCatching { UserType.valueOf(raw.uppercase()) }
            .getOrElse {
                throw BusinessRuleException(
                    "INVALID_USER_TYPE",
                    "Unknown account type $raw - expected STUDENT or INSTRUCTOR",
                )
            }
}
