package com.elearning.identity.application

import com.elearning.identity.domain.UserProfile
import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.platform.media.MediaService
import com.elearning.shared.errors.requireAllowed
import com.elearning.shared.errors.requireFound
import com.elearning.shared.errors.requireRule
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reading and editing your own profile.
 *
 * The row is created at registration and was, until now, write-once and
 * invisible: nobody could set a display name or an avatar afterwards.
 */
@Service
class UserProfileService(
    private val profiles: UserProfileRepository,
    private val mediaService: MediaService,
) {

    @Transactional(readOnly = true)
    fun get(userId: UUID): UserProfile =
        requireFound(profiles.findById(userId).orElse(null), "PROFILE_NOT_FOUND") { "Profile not found" }

    @Transactional
    fun update(userId: UUID, command: UpdateProfileCommand): UserProfile {
        val profile = requireFound(profiles.findById(userId).orElse(null), "PROFILE_NOT_FOUND") {
            "Profile not found"
        }

        command.firstName?.let { profile.firstName = it }
        command.lastName?.let { profile.lastName = it }
        command.bio?.let { profile.bio = it }
        command.timezone?.let { profile.timezone = it }
        command.language?.let { profile.language = it }

        command.avatarMediaId?.let { avatarId ->
            val media = mediaService.requireAvailable(avatarId)
            requireAllowed(media.createdBy == userId, "MEDIA_ACCESS_DENIED") {
                "That image is not yours"
            }
            // Same rule as course thumbnails: an avatar is shown wherever the
            // user appears, so it needs a stable URL rather than one that expires.
            requireRule(mediaService.publicUrlsFor(listOf(avatarId)).isNotEmpty(), "AVATAR_MUST_BE_PUBLIC") {
                "Upload the avatar with visibility PUBLIC"
            }
            profile.avatarMediaId = avatarId
        }
        return profile
    }

    @Transactional(readOnly = true)
    fun avatarUrl(profile: UserProfile): String? =
        profile.avatarMediaId?.let { mediaService.publicUrlsFor(listOf(it))[it] }
}

data class UpdateProfileCommand(
    val firstName: String? = null,
    val lastName: String? = null,
    val bio: String? = null,
    val timezone: String? = null,
    val language: String? = null,
    val avatarMediaId: UUID? = null,
)
