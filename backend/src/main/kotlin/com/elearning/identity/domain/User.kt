package com.elearning.identity.domain

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A platform account: authentication and account state only.
 *
 * Everything a person *shows* about themselves lives on [UserProfile], so this
 * table stays small and cheap to read on every authenticated request.
 *
 * Abstract, because there is no such thing as an account that is neither a
 * [Student] nor an [Instructor]. Which one it is, is settled when the account is
 * created and cannot change afterwards - a person who both learns and teaches
 * holds two accounts. That is the point: "may author courses" used to be a
 * boolean an administrator could toggle, which made the two kinds a spectrum
 * rather than a distinction.
 *
 * Mapped JOINED, so the shared half stays in `users` - still the table a login
 * looks up, and still what every foreign key points at - while each kind gets a
 * satellite table for what only it has. `user_type` is the discriminator, so
 * the kind can be read without joining.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "user_type", discriminatorType = DiscriminatorType.STRING)
abstract class User(

    @Column(nullable = false)
    var email: String,

    @Column(nullable = false)
    var username: String,

    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: UserStatus = UserStatus.PENDING,

) {
    /**
     * Which kind of account this is.
     *
     * Read off the subclass rather than mapped a second time: the column is
     * already the discriminator, and a settable copy of it is exactly the
     * mistake this replaced.
     */
    abstract val type: UserType

    val isInstructor: Boolean get() = type == UserType.INSTRUCTOR

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
