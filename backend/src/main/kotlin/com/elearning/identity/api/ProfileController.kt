package com.elearning.identity.api

import com.elearning.identity.application.UpdateProfileCommand
import com.elearning.identity.application.UserProfileService
import com.elearning.identity.domain.UserProfile
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(name = "UpdateProfileRequest", description = "Only the fields present are changed")
data class UpdateProfileRequest(
    @field:Size(max = 100) val firstName: String? = null,
    @field:Size(max = 100) val lastName: String? = null,
    val bio: String? = null,
    @field:Size(max = 64) val timezone: String? = null,
    @field:Size(max = 16) val language: String? = null,
    @get:Schema(description = "A completed upload of yours, made with visibility PUBLIC")
    val avatarMediaId: UUID? = null,
)

@Schema(name = "ProfileResponse")
data class ProfileResponse(
    val userId: UUID,
    val firstName: String?,
    val lastName: String?,
    val displayName: String?,
    val bio: String?,
    val timezone: String,
    val language: String,
    val avatarUrl: String?,
) {
    companion object {
        fun of(p: UserProfile, avatarUrl: String?) = ProfileResponse(
            userId = requireNotNull(p.userId),
            firstName = p.firstName,
            lastName = p.lastName,
            displayName = p.displayName,
            bio = p.bio,
            timezone = p.timezone,
            language = p.language,
            avatarUrl = avatarUrl,
        )
    }
}

@RestController
@RequestMapping("/api/v1/me/profile")
@Tag(name = "Me", description = "The authenticated user")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class ProfileController(
    private val profiles: UserProfileService,
    private val currentUser: CurrentUser,
) {

    @GetMapping
    @Operation(summary = "Read your profile")
    fun get(): ProfileResponse {
        val profile = profiles.get(currentUser.requireId())
        return ProfileResponse.of(profile, profiles.avatarUrl(profile))
    }

    @PatchMapping
    @Operation(summary = "Update your profile")
    fun update(@Valid @RequestBody request: UpdateProfileRequest): ProfileResponse {
        val profile = profiles.update(
            currentUser.requireId(),
            UpdateProfileCommand(
                firstName = request.firstName,
                lastName = request.lastName,
                bio = request.bio,
                timezone = request.timezone,
                language = request.language,
                avatarMediaId = request.avatarMediaId,
            ),
        )
        return ProfileResponse.of(profile, profiles.avatarUrl(profile))
    }
}
