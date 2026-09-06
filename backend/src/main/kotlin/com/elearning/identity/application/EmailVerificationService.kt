package com.elearning.identity.application

import com.elearning.identity.domain.EmailVerificationToken
import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.EmailVerificationTokenRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.platform.email.EmailSender
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.security.JwtProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Confirming that whoever registered actually controls the address.
 */
@Service
class EmailVerificationService(
    private val users: UserRepository,
    private val tokens: EmailVerificationTokenRepository,
    private val emails: EmailSender,
    private val jwtProperties: JwtProperties,
    private val identityProperties: IdentityProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Issues a link and mails it.
     *
     * Called *after* registration has committed, and never allowed to fail it:
     * an account that exists but whose mail bounced is recoverable with a
     * resend, whereas a 500 on register loses the account entirely and leaves
     * the address taken. Same rule the notification pipeline and the
     * certificate renderer already follow.
     */
    fun startVerification(user: User) {
        if (!identityProperties.requireEmailVerification) return
        runCatching { issueAndSend(requireNotNull(user.id), user.email) }
            .onFailure { log.warn("Could not send verification mail to {}: {}", user.email, it.message) }
    }

    /**
     * Re-sends a link, and says nothing about whether the address is known.
     *
     * The caller gets the same answer either way: this endpoint is unauthenticated,
     * so a different response for a registered address would turn it into a
     * membership oracle.
     */
    @Transactional
    fun resend(email: String) {
        val user = users.findByEmailIgnoreCase(email).orElse(null) ?: return
        if (user.status != UserStatus.PENDING) return
        runCatching { issueAndSend(requireNotNull(user.id), user.email) }
            .onFailure { log.warn("Could not resend verification mail: {}", it.message) }
    }

    @Transactional
    fun verify(rawToken: String, now: Instant = Instant.now()) {
        val record = tokens.findByTokenHash(SecureTokens.hash(rawToken)).orElse(null)
            ?: throw invalid()

        if (record.isConsumed) {
            // Distinguished on purpose: someone clicking their link twice should
            // be told they are already verified, not handed an error suggesting
            // the link was forged.
            throw BusinessRuleException("ALREADY_VERIFIED", "This address is already verified")
        }
        if (record.hasExpired(now)) throw invalid()

        val user = users.findById(record.userId).orElseThrow { invalid() }
        record.consume(now)
        // Only PENDING is promoted: a suspended account must not let itself back
        // in by clicking an old link.
        if (user.status == UserStatus.PENDING) {
            user.status = UserStatus.ACTIVE
            user.updatedAt = now
        }
    }

    @Transactional
    fun issueAndSend(userId: java.util.UUID, email: String) {
        // Outstanding links are spent first: two live links for one address
        // means a leaked older one still works after the newer was used.
        tokens.findByUserId(userId).forEach { it.consume() }

        val raw = SecureTokens.random()
        tokens.save(
            EmailVerificationToken(
                userId = userId,
                tokenHash = SecureTokens.hash(raw),
                expiresAt = Instant.now().plus(jwtProperties.emailVerificationTtl),
            ),
        )

        val link = "${identityProperties.appBaseUrl.trimEnd('/')}/verify-email?token=$raw"
        emails.send(
            to = email,
            subject = "Confirm your email address",
            body = "Welcome. Confirm your address to activate your account:\n\n$link\n\n" +
                "The link expires in ${jwtProperties.emailVerificationTtl.toHours()} hours. " +
                "If you did not create an account, ignore this message.",
        )
    }

    private fun invalid() =
        BusinessRuleException("INVALID_VERIFICATION_TOKEN", "That verification link is invalid or has expired")
}
