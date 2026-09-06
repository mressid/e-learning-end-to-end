package com.elearning.shared.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Validated
@ConfigurationProperties(prefix = "elearning.security.jwt")
data class JwtProperties(
    /**
     * HMAC signing secret. Must be at least 32 bytes for HS256 - Nimbus rejects
     * anything shorter, which is the behaviour we want rather than a weak key.
     */
    @field:NotBlank val secret: String = "",
    val issuer: String = "elearning",
    /**
     * Short by design. A signed JWT cannot be withdrawn - the resource server
     * checks a signature, not a table - so this window is exactly how long a
     * revoked session stays usable. The refresh token carries the revocable
     * part of the session.
     */
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    /** How long an email verification link stays good. */
    val emailVerificationTtl: Duration = Duration.ofDays(2),
    /** Short: a reset link in an inbox is a standing takeover of the account. */
    val passwordResetTtl: Duration = Duration.ofMinutes(30),
)
