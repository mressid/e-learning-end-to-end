package com.elearning.identity.api

import com.elearning.identity.infrastructure.UserRepository
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/me")
@Tag(name = "Me", description = "The authenticated user")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class MeController(
    private val users: UserRepository,
    private val currentUser: CurrentUser,
) {

    @GetMapping
    @Operation(summary = "Return the authenticated user's account")
    fun me(): UserResponse {
        // Through CurrentUser rather than the raw subject: administrators are a
        // separate table, so an admin token's subject is not a user id. Reading
        // it directly answered "user not found" for what is really a token
        // meant for a different audience.
        val userId = currentUser.requireId()
        val user = users.findById(userId)
            .orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }
        return UserResponse(
            id = requireNotNull(user.id),
            email = user.email,
            username = user.username,
            status = user.status.name,
            createdAt = user.createdAt,
        )
    }
}
