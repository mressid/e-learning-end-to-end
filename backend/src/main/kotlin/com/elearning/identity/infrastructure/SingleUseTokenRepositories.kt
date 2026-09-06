package com.elearning.identity.infrastructure

import com.elearning.identity.domain.EmailVerificationToken
import com.elearning.identity.domain.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface EmailVerificationTokenRepository : JpaRepository<EmailVerificationToken, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<EmailVerificationToken>

    /** For invalidating outstanding links when a new one is sent. */
    fun findByUserId(userId: UUID): List<EmailVerificationToken>
}

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByTokenHash(tokenHash: String): Optional<PasswordResetToken>

    fun findByUserId(userId: UUID): List<PasswordResetToken>
}
