package com.elearning.admin.application

import com.elearning.admin.domain.AdminUser
import com.elearning.admin.infrastructure.AdminUserRepository
import com.elearning.identity.application.AccessToken
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
import java.util.UUID

/**
 * Signing an administrator in.
 *
 * The token carries the admin's **permission codes** as authorities, so
 * `@PreAuthorize` can decide without a database round trip per request. The
 * cost is staleness: a signed token cannot be withdrawn, so a permission
 * removed now stays usable until the access token expires. Every path that
 * changes a role therefore revokes that admin's refresh tokens, which bounds
 * the window to one access-token lifetime instead of thirty days.
 */
@Service
class AdminAuthenticationService(
    private val admins: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtEncoder: JwtEncoder,
    private val jwtProperties: JwtProperties,
    private val refreshTokens: AdminRefreshTokenService,
    private val permissions: PermissionService,
) {

    @Transactional
    fun login(email: String, password: String): AdminSession {
        val admin = admins.findByEmailIgnoreCase(email).orElse(null)

        // Same shape as the learner login: one error for "no such account" and
        // "wrong password", and the password is hashed either way so the
        // response time does not reveal which administrators exist.
        val hash = admin?.passwordHash ?: DUMMY_HASH
        val matches = passwordEncoder.matches(password, hash)
        if (admin == null || !matches || !admin.canAuthenticate()) {
            throw ApiException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }

        admin.recordLogin()
        return sessionFor(admin)
    }

    @Transactional
    fun refresh(rawRefreshToken: String): AdminSession {
        val rotated = refreshTokens.rotate(rawRefreshToken)
        val admin = admins.findById(rotated.adminUserId).orElse(null)

        // Re-checked on every refresh: an account suspended an hour ago must
        // stop minting tokens, and this is the only moment to look again.
        if (admin == null || !admin.canAuthenticate()) {
            refreshTokens.revokeAllForAdmin(rotated.adminUserId)
            throw ApiException("INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token")
        }
        return AdminSession(
            accessToken = mintAccessToken(admin),
            refreshToken = rotated.refreshToken,
            permissions = permissions.permissionCodesFor(requireNotNull(admin.id)).sorted(),
        )
    }

    @Transactional
    fun logout(rawRefreshToken: String) = refreshTokens.revokeSession(rawRefreshToken)

    private fun sessionFor(admin: AdminUser): AdminSession {
        val id = requireNotNull(admin.id)
        return AdminSession(
            accessToken = mintAccessToken(admin),
            refreshToken = refreshTokens.issue(id),
            permissions = permissions.permissionCodesFor(id).sorted(),
        )
    }

    private fun mintAccessToken(admin: AdminUser): AccessToken {
        val id = requireNotNull(admin.id)
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(jwtProperties.accessTokenTtl)
        val codes = permissions.permissionCodesFor(id)

        val claims = JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(id.toString())
            .claim("username", admin.username)
            .claim(TokenSubject.CLAIM, TokenSubject.ADMIN)
            // Spring's resource server reads authorities from "scope" by
            // default, so the codes go there rather than needing a converter.
            .claim("scope", codes.joinToString(" "))
            .build()

        val token = jwtEncoder
            .encode(JwtEncoderParameters.from(JwsHeader.with { "HS256" }.build(), claims))
            .tokenValue

        return AccessToken(token, expiresAt, jwtProperties.accessTokenTtl.seconds)
    }

    private companion object {
        const val DUMMY_HASH = "{bcrypt}\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}

data class AdminSession(
    val accessToken: AccessToken,
    val refreshToken: IssuedAdminToken,
    val permissions: List<String>,
)
