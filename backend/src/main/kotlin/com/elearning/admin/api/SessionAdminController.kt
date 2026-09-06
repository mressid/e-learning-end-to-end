package com.elearning.admin.api

import com.elearning.admin.application.SessionAdminService
import com.elearning.identity.application.UserLookupService
import com.elearning.identity.infrastructure.SessionRow
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "SessionResponse", description = "One live login, not one token")
data class SessionResponse(
    @get:Schema(description = "Identifies the session; pass it back to end this one")
    val sessionId: UUID,
    val subjectId: UUID,
    val subjectLabel: String?,
    val startedAt: Instant,
    val lastUsedAt: Instant,
    val expiresAt: Instant,
    @get:Schema(description = "Refreshes so far - a rough measure of how active the session is")
    val refreshCount: Long,
)

/**
 * Live sessions, for the Security section of the dashboard's `/settings`.
 *
 * The rest of that page is deliberately absent: theme is a per-viewer browser
 * preference with nothing for a server to store, localization needs the
 * application to be localized first, token lifetimes belong to the environment,
 * and webhooks are a delivery subsystem rather than a setting. This is the part
 * with data behind it.
 */
@RestController
@RequestMapping("/api/v1/admin/sessions")
@Tag(name = "Admin: sessions", description = "Who is signed in, and ending it")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class SessionAdminController(
    private val sessions: SessionAdminService,
    private val people: UserLookupService,
) {

    @GetMapping
    @Operation(
        summary = "Live learner sessions, most recently used first",
        description = "Requires `settings.manage`. One entry per login, not per token: " +
            "rotation mints a successor on every refresh, so counting tokens would " +
            "report one login as dozens of sessions. Filter with `userId`.",
    )
    fun list(
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<SessionResponse> {
        val results = sessions.listUserSessions(userId, PageRequest.of(page, size))
        val labels = people.summaries(results.content.map { it.subjectId })
        return PageResponse.from(results) { toResponse(it, labels[it.subjectId]?.label) }
    }

    @GetMapping("/admins")
    @Operation(
        summary = "Live administrator sessions",
        description = "Requires `settings.manage`. Administrators are a separate table, " +
            "so their sessions are a separate chain and a separate listing.",
    )
    fun listAdmins(
        @RequestParam(required = false) adminId: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<SessionResponse> {
        val results = sessions.listAdminSessions(adminId, PageRequest.of(page, size))
        return PageResponse.from(results) { toResponse(it, null) }
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "End one learner session",
        description = "Requires `settings.manage`. The access token already issued stays " +
            "valid until it expires - a signed JWT cannot be withdrawn - which is why " +
            "that lifetime is short. Recorded in the audit trail.",
    )
    fun revoke(@PathVariable sessionId: UUID) = sessions.revokeUserSession(sessionId)

    @DeleteMapping("/admins/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "End one administrator session", description = "Requires `settings.manage`.")
    fun revokeAdmin(@PathVariable sessionId: UUID) = sessions.revokeAdminSession(sessionId)

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Sign a learner out everywhere",
        description = "Requires `settings.manage`. For a reported compromise, where " +
            "ending one device is not enough.",
    )
    fun revokeAllForUser(@PathVariable userId: UUID) = sessions.revokeAllUserSessions(userId)

    private fun toResponse(row: SessionRow, label: String?) = SessionResponse(
        sessionId = row.familyId,
        subjectId = row.subjectId,
        subjectLabel = label,
        startedAt = row.startedAt,
        lastUsedAt = row.lastUsedAt,
        expiresAt = row.expiresAt,
        refreshCount = row.tokenCount - 1,
    )
}
