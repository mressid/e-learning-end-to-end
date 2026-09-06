package com.elearning.identity.infrastructure

import com.elearning.platform.media.MediaReferenceProbe
import org.springframework.stereotype.Component
import java.util.UUID

/** Identity's answer: someone is using the file as their avatar. */
@Component
class AvatarMediaReferenceProbe(private val profiles: UserProfileRepository) : MediaReferenceProbe {
    override fun isReferenced(mediaId: UUID): Boolean = profiles.existsByAvatarMediaId(mediaId)
    override fun describe(): String = "a profile avatar"
}
