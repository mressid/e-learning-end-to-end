package com.elearning.shared.testing

import com.elearning.identity.domain.Instructor
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.InstructorRepository
import com.elearning.identity.infrastructure.UserProfileRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Instructor accounts for tests, made directly rather than through an endpoint.
 *
 * `/api/v1/auth/register` produces a student and always will - authoring is not
 * something an account can grant itself - so a test that needs somebody who can
 * own a course would otherwise have to sign in as an administrator first, which
 * is a lot of setup to assert something about courses.
 */
@Component
class TestAccounts(
    private val instructors: InstructorRepository,
    private val profiles: UserProfileRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /** An ACTIVE instructor who can sign in at `/api/v1/auth/login` with [password]. */
    @Transactional
    fun instructor(email: String, username: String, password: String): String {
        val user = instructors.save(
            Instructor(
                email = email.lowercase(),
                username = username,
                passwordHash = requireNotNull(passwordEncoder.encode(password)),
                status = UserStatus.ACTIVE,
            ),
        )
        profiles.save(UserProfile(user = user))
        return requireNotNull(user.id).toString()
    }
}
