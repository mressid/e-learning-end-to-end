package com.elearning.identity.application

import com.elearning.identity.domain.PasswordResetToken
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.PasswordResetTokenRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.platform.email.EmailSender
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.security.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Recovering an account whose password is lost.
 *
 * A reset link sitting in an inbox is a standing takeover of the account, which
 * drives three of the rules here: the link is short-lived, it works exactly
 * once, and redeeming it ends every existing session.
 */
@Service
class PasswordResetService(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokens: RefreshTokenService,
    private val emails: EmailSender,
    private val jwtProperties: JwtProperties,
    private val identityProperties: IdentityProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Always succeeds from the caller's point of view.
     *
     * "No account with that address" would make this endpoint a membership
     * oracle that anybody can query without authenticating - the same reason
     * login refuses to distinguish a wrong password from an unknown account.
     */
    @Transactional
    fun request(email: String) {
        val user = users.findByEmailIgnoreCase(email).orElse(null) ?: return
        // A suspended account must not be recoverable by whoever asks.
        if (user.status == UserStatus.SUSPENDED || user.status == UserStatus.DISABLED) return

        val userId = requireNotNull(user.id)
        // Any outstanding link is spent: the newest request is the real one, and
        // leaving older links live widens the window for a leaked inbox.
        tokens.findByUserId(userId).forEach { it.consume() }

        val raw = SecureTokens.random()
        tokens.save(
            PasswordResetToken(
                userId = userId,
                tokenHash = SecureTokens.hash(raw),
                expiresAt = Instant.now().plus(jwtProperties.passwordResetTtl),
            ),
        )

        val link = "${identityProperties.appBaseUrl.trimEnd('/')}/reset-password?token=$raw"
        runCatching {
            emails.send(
                to = user.email,
                subject = "Reset your password",
                body = "Someone asked to reset the password for this account:\n\n$link\n\n" +
                    "The link expires in ${jwtProperties.passwordResetTtl.toMinutes()} minutes " +
                    "and can be used once. If this was not you, ignore this message - " +
                    "your password has not changed.",
            )
        }.onFailure { log.warn("Could not send reset mail: {}", it.message) }
    }

    @Transactional
    fun confirm(rawToken: String, newPassword: String, now: Instant = Instant.now()) {
        val record = tokens.findByTokenHash(SecureTokens.hash(rawToken)).orElse(null)
            ?: throw invalid()
        if (!record.isRedeemable(now)) throw invalid()

        val user = users.findById(record.userId).orElseThrow { invalid() }
        record.consume(now)
        user.passwordHash = requireNotNull(passwordEncoder.encode(newPassword))
        user.updatedAt = now

        // Whoever prompted the reset may already hold a session. Changing the
        // password without ending them would leave the intruder signed in, which
        // is the one outcome a reset exists to prevent.
        refreshTokens.revokeAllForUser(record.userId, now)

        // Proving control of the address is at least as strong as clicking a
        // verification link, so a reset also settles a pending registration.
        if (user.status == UserStatus.PENDING) user.status = UserStatus.ACTIVE
    }

    private fun invalid() =
        BusinessRuleException("INVALID_RESET_TOKEN", "That reset link is invalid or has expired")
}
