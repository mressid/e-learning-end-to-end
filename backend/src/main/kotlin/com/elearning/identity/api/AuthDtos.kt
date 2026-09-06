package com.elearning.identity.api

import com.elearning.identity.application.AuthenticatedSession
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(name = "RegisterRequest")
data class RegisterRequest(
    @field:NotBlank @field:Email
    @field:Size(max = 320)
    @get:Schema(example = "student@example.com")
    val email: String,

    @field:NotBlank
    @field:Size(min = 3, max = 64)
    @field:Pattern(
        regexp = "^[a-zA-Z0-9._-]+$",
        message = "may only contain letters, digits, dots, underscores and hyphens",
    )
    @get:Schema(example = "jane.doe")
    val username: String,

    @field:NotBlank
    @field:Size(min = 12, max = 128, message = "must be between 12 and 128 characters")
    @get:Schema(description = "Stored only as a hash", example = "correct horse battery staple")
    val password: String,

    @field:Size(max = 100)
    val firstName: String? = null,

    @field:Size(max = 100)
    val lastName: String? = null,
)

@Schema(name = "LoginRequest")
data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

@Schema(name = "TokenResponse")
data class TokenResponse(
    @get:Schema(description = "Send as: Authorization: Bearer <accessToken>")
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val expiresAt: Instant,
    @get:Schema(
        description = "Use once at /auth/refresh to get the next pair. Each refresh " +
            "token works exactly once; the response carries its replacement.",
    )
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
) {
    companion object {
        fun of(session: AuthenticatedSession) = TokenResponse(
            accessToken = session.accessToken.token,
            expiresInSeconds = session.accessToken.expiresInSeconds,
            expiresAt = session.accessToken.expiresAt,
            refreshToken = session.refreshToken.token,
            refreshTokenExpiresAt = session.refreshToken.expiresAt,
        )
    }
}

@Schema(name = "RefreshRequest")
data class RefreshRequest(@field:NotBlank val refreshToken: String)

@Schema(name = "UserResponse")
data class UserResponse(
    val id: UUID,
    val email: String,
    val username: String,
    val status: String,
    val createdAt: Instant,
)

@Schema(name = "VerifyEmailRequest")
data class VerifyEmailRequest(@field:NotBlank val token: String)

@Schema(name = "EmailRequest", description = "Answered identically whether or not the address is known")
data class EmailRequest(@field:NotBlank @field:Email val email: String)

@Schema(name = "PasswordResetRequest")
data class PasswordResetConfirmRequest(
    @field:NotBlank val token: String,
    @field:NotBlank @field:Size(min = 12, max = 128)
    @get:Schema(description = "At least 12 characters")
    val newPassword: String,
)
