package com.elearning.learning.api

import com.elearning.identity.application.UserLookupService
import com.elearning.learning.application.CourseCatalog
import com.elearning.learning.domain.Certificate
import com.elearning.learning.infrastructure.CertificateRepository
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.NotFoundException
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "AdminCertificateResponse")
data class AdminCertificateResponse(
    val id: UUID,
    val certificateNumber: String,
    val studentId: UUID,
    val studentName: String?,
    val studentEmail: String?,
    val courseId: UUID,
    val courseTitle: String?,
    val issuedAt: Instant,
    val revokedAt: Instant?,
    val valid: Boolean,
)

/**
 * The issued-credential register, for the dashboard's `/certificates` page.
 *
 * Read-only. Revocation already exists on the learner-facing controller behind
 * `certificate.revoke`, and duplicating it here would be a second path to the
 * same state change with its own chance of drifting.
 *
 * The **verification code is deliberately absent** from every response. It is
 * the unguessable half of the public check at
 * `GET /certificates/verify/{code}`; listing codes for staff would turn a
 * dashboard screenshot into a set of forgeable credentials.
 */
@RestController
@RequestMapping("/api/v1/admin/certificates")
@Tag(name = "Admin: certificates", description = "Issued credentials")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class AdminCertificateController(
    private val certificates: CertificateRepository,
    private val catalog: CourseCatalog,
    private val people: UserLookupService,
    private val platformAccess: PlatformAccess,
) {

    @GetMapping
    @Operation(
        summary = "List issued certificates",
        description = "Requires `certificate.read`. Filter by `courseId`, or by " +
            "`revoked=true|false`. Verification codes are never returned.",
    )
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(required = false) courseId: UUID?,
        @RequestParam(required = false) revoked: Boolean?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<AdminCertificateResponse> {
        platformAccess.require("certificate.read")

        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt"))
        val results = when {
            courseId != null -> certificates.findByCourseId(courseId, pageable)
            revoked != null -> certificates.findByRevoked(revoked, pageable)
            else -> certificates.findAll(pageable)
        }
        return PageResponse.from(results, decorate(results.content))
    }

    @GetMapping("/{certificateId}")
    @Operation(summary = "One certificate", description = "Requires `certificate.read`.")
    @Transactional(readOnly = true)
    fun get(@PathVariable certificateId: UUID): AdminCertificateResponse {
        platformAccess.require("certificate.read")
        val certificate = certificates.findById(certificateId)
            .orElseThrow { NotFoundException("CERTIFICATE_NOT_FOUND", "Certificate not found") }
        return decorate(listOf(certificate))(certificate)
    }

    /** Resolves holders and titles once for the page, not once per row. */
    private fun decorate(rows: List<Certificate>): (Certificate) -> AdminCertificateResponse {
        val holders = people.summaries(rows.map { it.studentId })
        val titles = rows.map { it.courseId }.distinct().associateWith { catalog.courseTitle(it) }
        return { c ->
            val holder = holders[c.studentId]
            AdminCertificateResponse(
                id = requireNotNull(c.id),
                certificateNumber = c.certificateNumber,
                studentId = c.studentId,
                studentName = holder?.label,
                studentEmail = holder?.email,
                courseId = c.courseId,
                courseTitle = titles[c.courseId],
                issuedAt = c.issuedAt,
                revokedAt = c.revokedAt,
                valid = c.isValid,
            )
        }
    }
}
