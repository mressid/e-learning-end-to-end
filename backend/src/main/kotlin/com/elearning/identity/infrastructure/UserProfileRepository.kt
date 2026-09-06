package com.elearning.identity.infrastructure

import com.elearning.identity.domain.UserProfile
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserProfileRepository : JpaRepository<UserProfile, UUID> {
    fun existsByAvatarMediaId(avatarMediaId: UUID): Boolean
}
