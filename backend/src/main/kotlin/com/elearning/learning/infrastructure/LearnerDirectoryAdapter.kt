package com.elearning.learning.infrastructure

import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.learning.application.LearnerDirectory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LearnerDirectoryAdapter(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
) : LearnerDirectory {

    /** Real name when the profile has one; otherwise the username. */
    override fun displayNameOf(userId: UUID): String? =
        profiles.findById(userId).map { it.displayName }.orElse(null)
            ?: users.findById(userId).map { it.username }.orElse(null)
}
