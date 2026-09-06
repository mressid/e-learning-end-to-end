package com.elearning.admin.application

import com.elearning.admin.infrastructure.AdminUserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.env.Environment
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

/**
 * Says so, loudly, when the seeded super admin still has its development
 * password.
 *
 * V6 has to seed an administrator - the endpoint that grants the role requires
 * somebody who already holds it - and the default has to be usable or a fresh
 * clone cannot sign in. The risk is that it survives into an environment that
 * matters, silently, because nothing ever mentions it again. A log line at
 * every startup is cheap and hard to miss.
 *
 * Checked by *verifying the password*, not by comparing hashes: bcrypt salts
 * every hash differently, so the seeded string and a re-hash of the same
 * password never match textually.
 */
@Component
class DefaultSuperAdminWarning(
    private val admins: AdminUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val environment: Environment,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun warnIfDefaultPasswordInUse() {
        val email = environment.getProperty("spring.flyway.placeholders.superAdminEmail") ?: return
        val admin = admins.findByEmailIgnoreCase(email).orElse(null) ?: return

        if (passwordEncoder.matches(DEFAULT_PASSWORD, admin.passwordHash)) {
            log.warn(
                "The seeded super admin ({}) still has the default development password. " +
                    "Set SUPER_ADMIN_PASSWORD_HASH before this reaches anywhere real, " +
                    "or change it via POST /api/v1/admin/auth/me/password.",
                email,
            )
        }
    }

    private companion object {
        /** Matches the bcrypt hash defaulted in `application.yaml`. */
        const val DEFAULT_PASSWORD = "change this password now"
    }
}
