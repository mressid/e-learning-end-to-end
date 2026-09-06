package com.elearning.identity.application

import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.shared.errors.ApiException
import com.elearning.shared.security.JwtProperties
import com.elearning.shared.security.TokenSubject
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Verifies credentials and issues an access token.
 */
@Service
class AuthenticationService(
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val refreshTokens: RefreshTokenService,
) {

    @Transactional
    fun login(email: String, password: String): AuthenticatedSession {
        val user = users.findByEmailIgnoreCase(email).orElse(null)

        // One error for "no such user" and "wrong password", and the password is
        // still hashed when the user is missing, so neither the response nor the
        // response time reveals whether an account exists.
        val hash = user?.passwordHash ?: DUMMY_HASH
        val passwordMatches = passwordEncoder.matches(password, hash)

        if (user == null || !passwordMatches) {
            throw ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        // Told apart from bad credentials only once the password is already
        // known to be right. At that point the caller has proved they hold the
        // credentials, so naming the reason reveals nothing they could not
        // establish anyway - and leaving them staring at "invalid email or
        // password" after a successful sign-up is a dead end.
        if (user.status == UserStatus.PENDING) {
            throw ApiException(
                "EMAIL_NOT_VERIFIED",
                HttpStatus.FORBIDDEN,
                "Confirm your email address before signing in",
            )
        }
        // Suspended and disabled stay indistinguishable from bad credentials:
        // unlike a pending address, there is nothing the caller can do about it
        // and the state is not theirs to learn.
        if (!user.canAuthenticate()) {
            throw ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        user.recordLogin()

        val userId = requireNotNull(user.id)
        return AuthenticatedSession(
            accessToken = mintAccessToken(userId, user.username),
            refreshToken = refreshTokens.issue(userId),
        )
    }

    /**
     * Exchanges a refresh token for a fresh pair.
     *
     * The account is re-checked on every refresh, not just at login: an account
     * suspended an hour ago must stop being able to mint access tokens, and the
     * refresh call is the only moment the server gets to look again.
     */
    @Transactional
    fun refresh(rawRefreshToken: String): AuthenticatedSession {
        val rotated = refreshTokens.rotate(rawRefreshToken)
        val user = users.findById(rotated.userId).orElse(null)

        if (user == null || !user.canAuthenticate()) {
            // The presented token was already rotated away by `rotate`, so a
            // disabled account cannot keep refreshing.
            refreshTokens.revokeAllForUser(rotated.userId)
            throw ApiException(
                "INVALID_REFRESH_TOKEN",
                HttpStatus.UNAUTHORIZED,
                "Invalid or expired refresh token",
            )
        }

        return AuthenticatedSession(
            accessToken = mintAccessToken(rotated.userId, user.username),
            refreshToken = rotated.refreshToken,
        )
    }

    /** Ends the session the refresh token belongs to. */
    @Transactional
    fun logout(rawRefreshToken: String) = refreshTokens.revokeSession(rawRefreshToken)

    private fun mintAccessToken(userId: java.util.UUID, username: String): AccessToken {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)
        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(userId.toString())
            .claim("username", username)
            // Marks this as a learner token. Administrators are a different
            // table, so a token without a type would make the two identity
            // spaces interchangeable to anything that reads one.
            .claim(TokenSubject.CLAIM, TokenSubject.USER)
            .build()

        val token = jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with { "HS256" }.build(), claims))
            .tokenValue

        return AccessToken(
            token = token,
            expiresAt = expiresAt,
            expiresInSeconds = jwtProperties.accessTokenTtl.seconds,
        )
    }

    private companion object {
        /** A real bcrypt hash of a value nobody can supply; only its cost matters. */
        const val DUMMY_HASH = "{bcrypt}\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}

/** What a successful login or refresh hands back. */
data class AuthenticatedSession(
    val accessToken: AccessToken,
    val refreshToken: IssuedRefreshToken,
)

data class AccessToken(
    val token: String,
    val expiresAt: Instant,
    val expiresInSeconds: Long,
)
