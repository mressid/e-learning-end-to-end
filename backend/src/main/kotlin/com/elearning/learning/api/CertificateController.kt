package com.elearning.learning.api

import com.elearning.learning.application.CertificateDocumentService
import com.elearning.learning.application.CertificateService
import com.elearning.learning.application.CertificateVerification
import com.elearning.learning.domain.Certificate
import com.elearning.shared.api.OpenApiConfig
import com.elearning.platform.media.StorageProperties
import com.elearning.shared.security.CurrentActor
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "CertificateResponse")
data class CertificateResponse(
    val id: UUID,
    val courseId: UUID,
    val certificateNumber: String,
    @get:Schema(description = "Share this to let someone verify the certificate")
    val verificationCode: String,
    val issuedAt: Instant,
    val revokedAt: Instant?,
) {
    companion object {
        fun of(c: Certificate) = CertificateResponse(
            id = requireNotNull(c.id),
            courseId = c.courseId,
            certificateNumber = c.certificateNumber,
            verificationCode = c.verificationCode,
            issuedAt = c.issuedAt,
            revokedAt = c.revokedAt,
        )
    }
}

/** Public view: proves the certificate without exposing who else holds one. */
@Schema(name = "CertificateVerificationResponse")
data class CertificateVerificationResponse(
    val certificateNumber: String,
    val courseId: UUID,
    val issuedAt: Instant,
    val valid: Boolean,
    val revokedAt: Instant?,
) {
    companion object {
        fun of(v: CertificateVerification) = CertificateVerificationResponse(
            certificateNumber = v.certificateNumber,
            courseId = v.courseId,
            issuedAt = v.issuedAt,
            valid = v.valid,
            revokedAt = v.revokedAt,
        )
    }
}

@Schema(name = "CertificateDownloadUrlResponse")
data class CertificateDownloadUrlResponse(val downloadUrl: String, val expiresInSeconds: Long)

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Certificates", description = "Course completion certificates")
class CertificateController(
    private val certificates: CertificateService,
    private val documents: CertificateDocumentService,
    private val storageProperties: StorageProperties,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @GetMapping("/me/certificates")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Your certificates")
    fun mine(): List<CertificateResponse> =
        certificates.forStudent(currentUser.requireId()).map(CertificateResponse::of)

    @GetMapping("/certificates/verify/{code}")
    @Operation(
        summary = "Verify a certificate",
        description = "Public: an employer holding the code can check it without an account.",
    )
    fun verify(@PathVariable code: String): CertificateVerificationResponse =
        CertificateVerificationResponse.of(certificates.verify(code))

    @GetMapping("/certificates/{certificateId}/download-url")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Short-lived URL for the certificate PDF",
        description = "The holder or the course's staff. Returns 422 while the document is still rendering.",
    )
    fun downloadUrl(@PathVariable certificateId: UUID): CertificateDownloadUrlResponse =
        CertificateDownloadUrlResponse(
            downloadUrl = documents.downloadUrl(certificateId, currentUser.requireId()).toString(),
            expiresInSeconds = storageProperties.presignedUrlTtl.seconds,
        )

    @PostMapping("/certificates/{certificateId}/revoke")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Revoke a certificate", description = "Course staff only.")
    fun revoke(@PathVariable certificateId: UUID): CertificateResponse =
        CertificateResponse.of(certificates.revoke(certificateId, currentActor.requireId()))
}
