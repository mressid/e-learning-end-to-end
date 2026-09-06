package com.elearning.identity.api

import com.elearning.identity.application.AuthenticationService
import com.elearning.identity.application.EmailVerificationService
import com.elearning.identity.application.PasswordResetService
import com.elearning.identity.application.RegisterUserCommand
import com.elearning.identity.application.UserRegistrationService
import com.elearning.shared.errors.ApiError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Account registration and token issuance")
class AuthController(
    private val registrationService: UserRegistrationService,
    private val authenticationService: AuthenticationService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
) {

    @PostMapping("/register")
    @Operation(summary = "Register a new account")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Account created"),
        ApiResponse(
            responseCode = "409",
            description = "Email or username already taken",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Validation failed",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<UserResponse> {
        val user = registrationService.register(
            RegisterUserCommand(
                email = request.email,
                username = request.username,
                password = request.password,
                firstName = request.firstName,
                lastName = request.lastName,
            ),
        )
        // Deliberately after `register` has returned, so the mail goes out only
        // once the account has actually committed - and it cannot fail the
        // registration, because an account whose mail bounced is recoverable
        // with a resend while a failed register loses the account and leaves
        // the address taken.
        emailVerificationService.startVerification(user)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            UserResponse(
                id = requireNotNull(user.id),
                email = user.email,
                username = user.username,
                status = user.status.name,
                createdAt = user.createdAt,
            ),
        )
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for an access token")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Token issued"),
        ApiResponse(
            responseCode = "401",
            description = "Invalid credentials",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        TokenResponse.of(authenticationService.login(request.email, request.password))

    @PostMapping("/refresh")
    @Operation(
        summary = "Exchange a refresh token for a new pair",
        description = "The presented token is consumed. Presenting a token that " +
            "was already used revokes the whole session, because a token working " +
            "twice means a copy of it is in circulation.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "A new pair"),
        ApiResponse(
            responseCode = "401",
            description = "Unknown, expired, or already-used refresh token",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse =
        TokenResponse.of(authenticationService.refresh(request.refreshToken))

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "End the session",
        description = "Revokes the refresh token and everything rotated from it. " +
            "The access token already issued cannot be withdrawn - it is a signed " +
            "JWT - so it stays valid until it expires, which is why its lifetime " +
            "is short. Idempotent: logging out twice is not an error.",
    )
    fun logout(@Valid @RequestBody request: RefreshRequest) =
        authenticationService.logout(request.refreshToken)

    // ---- email verification ---------------------------------------------

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Confirm an email address",
        description = "Activates the account. The link works once and expires.",
    )
    fun verifyEmail(@Valid @RequestBody request: VerifyEmailRequest) =
        emailVerificationService.verify(request.token)

    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Send the confirmation link again",
        description = "Always 202, whether or not the address is registered - a " +
            "different answer would turn this into a membership oracle.",
    )
    fun resendVerification(@Valid @RequestBody request: EmailRequest) =
        emailVerificationService.resend(request.email)

    // ---- password reset --------------------------------------------------

    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Ask for a reset link",
        description = "Always 202, whether or not the address is registered.",
    )
    fun requestPasswordReset(@Valid @RequestBody request: EmailRequest) =
        passwordResetService.request(request.email)

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Set a new password with a reset link",
        description = "Consumes the link and signs out every existing session, " +
            "so a password change locks out anyone already holding one.",
    )
    fun confirmPasswordReset(@Valid @RequestBody request: PasswordResetConfirmRequest) =
        passwordResetService.confirm(request.token, request.newPassword)
}
