package com.elearning.identity.domain

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
 * A platform account: authentication and account state only.
 *
 * Everything a person *shows* about themselves lives on [UserProfile], so this
 * table stays small and cheap to read on every authenticated request.
 */
@Entity
@Table(name = "users")
class User(

    @Column(nullable = false)
    var email: String,

    @Column(nullable = false)
    var username: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.PENDING,

    /**
     * May author courses.
     *
     * Deliberately not authority over any *particular* course - that is still
     * ownership or co-instructorship (§11). This answers only "may this person
     * start one", which no relationship can express, because the course does not
     * exist yet to have a relationship with.
     */
    @Column(name = "is_instructor", nullable = false)
    var isInstructor: Boolean = false,
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

    fun recordLogin(at: Instant = Instant.now()) {
        lastLoginAt = at
    }

    /** Only ACTIVE accounts may authenticate. */
    fun canAuthenticate(): Boolean = status == UserStatus.ACTIVE
}
