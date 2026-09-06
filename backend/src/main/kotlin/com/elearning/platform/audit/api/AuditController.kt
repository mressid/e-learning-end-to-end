package com.elearning.platform.audit.api

import com.elearning.platform.audit.AuditEntry
import com.elearning.platform.audit.AuditRepository
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.security.PlatformAccess
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "AuditEntryResponse")
data class AuditEntryResponse(
    val id: UUID,
    val occurredAt: Instant,
    val actorType: String,
    val actorId: UUID?,
    @get:Schema(description = "Who the actor was at the time, not who that id resolves to now")
    val actorLabel: String?,
    val action: String,
    val targetType: String?,
    val targetId: UUID?,
    val summary: String,
    val details: Map<String, Any>,
    @get:Schema(description = "Matches the X-Request-Id header and the application logs")
    val requestId: String?,
) {
    companion object {
        fun of(e: AuditEntry) = AuditEntryResponse(
            id = requireNotNull(e.id),
            occurredAt = e.occurredAt,
            actorType = e.actorType.name,
            actorId = e.actorId,
            actorLabel = e.actorLabel,
            action = e.action,
            targetType = e.targetType,
            targetId = e.targetId,
            summary = e.summary,
            details = e.details,
            requestId = e.requestId,
        )
    }
}

/**
 * The audit trail, for the dashboard's `/audit-logs` page.
 *
 * **Read-only, and that is the whole point.** There is no endpoint here that
 * writes, edits or deletes an entry - records are written by the operations
 * being audited, in their own transactions. A log with a delete button is not
 * evidence of anything.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Admin: audit", description = "What happened, and who did it")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AuditController(
    private val entries: AuditRepository,
    private val platformAccess: PlatformAccess,
) {

    @GetMapping
    @Operation(
        summary = "Read the audit trail, newest first",
        description = "Requires `audit.read`. Filter by `action`, by `actorId` to follow " +
            "one person, or by `targetType` + `targetId` to see everything that " +
            "happened to one thing.",
    )
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) actorId: UUID?,
        @RequestParam(required = false) targetType: String?,
        @RequestParam(required = false) targetId: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): PageResponse<AuditEntryResponse> {
        platformAccess.require("audit.read")

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))
        val results = when {
            !action.isNullOrBlank() -> entries.findByAction(action, pageable)
            actorId != null -> entries.findByActorId(actorId, pageable)
            targetType != null && targetId != null ->
                entries.findByTargetTypeAndTargetId(targetType, targetId, pageable)
            else -> entries.findAll(pageable)
        }
        return PageResponse.from(results, AuditEntryResponse::of)
    }
}
