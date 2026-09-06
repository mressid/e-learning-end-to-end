package com.elearning.admin.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * Someone who administers the platform, as opposed to someone who learns on it.
 *
 * A table of its own rather than a flag on `users`: keeping the two apart means
 * a self-registered learner has no path to a role at all. That is a structural
 * guarantee rather than a rule somebody has to remember every time they touch
 * registration.
 *
 * There is no PENDING status. Admins do not self-register, so the two things
 * email verification establishes - that the address is real, and that its owner
 * consented - are already true by the time a colleague creates the account.
 */
@Entity
@Table(name = "admin_users")
class AdminUser(

    @Column(nullable = false)
    var email: String,

    @Column(nullable = false)
    var username: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AdminStatus = AdminStatus.ACTIVE,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    @Column(name = "last_login_at")
    var lastLoginAt: Instant? = null

    fun canAuthenticate(): Boolean = status == AdminStatus.ACTIVE

    fun recordLogin(at: Instant = Instant.now()) {
        lastLoginAt = at
    }
}

enum class AdminStatus { ACTIVE, SUSPENDED, DISABLED }
