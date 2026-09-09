package com.elearning.identity.application

import com.elearning.identity.domain.Student
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.shared.errors.ConflictException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Registering an account: one business operation, one transaction (§14).
 */
@Service
class UserRegistrationService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
    private val identityProperties: IdentityProperties,
) {

    /**
     * Self-registration always produces a [Student].
     *
     * There is no public path to an instructor account, and that is deliberate:
     * authoring is not something you can grant yourself. An administrator
     * creates instructors through the admin directory.
     */
    @Transactional
    fun register(command: RegisterUserCommand): Student {
        // Checked explicitly so the caller gets a precise error code. The unique
        // constraints in the database remain the actual guarantee against races.
        if (users.existsByEmailIgnoreCase(command.email)) {
            throw ConflictException("EMAIL_ALREADY_REGISTERED", "That email address is already registered")
        }
        if (users.existsByUsernameIgnoreCase(command.username)) {
            throw ConflictException("USERNAME_TAKEN", "That username is already taken")
        }

        val user = users.save(
            Student(
                email = command.email.lowercase(),
                username = command.username,
                passwordHash = requireNotNull(passwordEncoder.encode(command.password)),
                // PENDING until the address is confirmed. A deployment that
                // establishes identity some other way can turn the requirement
                // off, in which case there is nothing to wait for.
                status = if (identityProperties.requireEmailVerification) {
                    UserStatus.PENDING
                } else {
                    UserStatus.ACTIVE
                },
            ),
        )

        profiles.save(
            UserProfile(
                user = user,
                firstName = command.firstName,
                lastName = command.lastName,
            ),
        )

        return user
    }
}

data class RegisterUserCommand(
    val email: String,
    val username: String,
    val password: String,
    val firstName: String?,
    val lastName: String?,
)
