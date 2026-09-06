package com.elearning.learning.application

import com.elearning.learning.domain.Certificate
import com.elearning.learning.infrastructure.CertificateRepository
import com.elearning.shared.security.PlatformAccess
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import java.util.UUID

/**
 * Issuing and verifying certificates.
 */
@Service
class CertificateService(
    private val certificates: CertificateRepository,
    private val catalog: CourseCatalog,
    private val platformAccess: PlatformAccess,
    private val audit: AuditService,
) {

    /**
     * Issues a certificate, or returns the existing one.
     *
     * Idempotent because completion can be reached more than once - a student
     * who re-completes a re-opened course must not collect a second certificate.
     */
    @Transactional
    fun issue(studentId: UUID, courseId: UUID): Certificate =
        certificates.findByStudentIdAndCourseId(studentId, courseId).orElseGet {
            certificates.save(
                Certificate(
                    studentId = studentId,
                    courseId = courseId,
                    certificateNumber = nextCertificateNumber(),
                    verificationCode = newVerificationCode(),
                ),
            )
        }

    @Transactional(readOnly = true)
    fun forStudent(studentId: UUID): List<Certificate> =
        certificates.findByStudentIdOrderByIssuedAtDesc(studentId)

    /**
     * Public verification. Takes the code, not an id: anyone holding a printed
     * certificate can check it without an account.
     */
    @Transactional(readOnly = true)
    fun verify(code: String): CertificateVerification {
        val certificate = certificates.findByVerificationCode(code)
            .orElseThrow { NotFoundException("CERTIFICATE_NOT_FOUND", "No certificate matches that code") }
        return CertificateVerification(
            certificateNumber = certificate.certificateNumber,
            courseId = certificate.courseId,
            issuedAt = certificate.issuedAt,
            valid = certificate.isValid,
            revokedAt = certificate.revokedAt,
        )
    }

    /** Revocation is for course staff, e.g. after an academic-integrity finding. */
    @Transactional
    fun revoke(certificateId: UUID, editorId: UUID): Certificate {
        val certificate = certificates.findById(certificateId)
            .orElseThrow { NotFoundException("CERTIFICATE_NOT_FOUND", "Certificate not found") }
        if (!catalog.canEdit(certificate.courseId, editorId) &&
            !platformAccess.has("certificate.revoke")
        ) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to manage this course")
        }
        certificate.revoke()
        audit.record(
            action = "certificate.revoked",
            summary = "Revoked certificate ${'$'}{certificate.certificateNumber}",
            targetType = "CERTIFICATE",
            targetId = certificateId,
            details = mapOf("courseId" to certificate.courseId.toString()),
        )
        return certificate
    }

    private fun nextCertificateNumber(): String {
        val year = ZonedDateTime.now(ZoneOffset.UTC).year
        // Random rather than sequential: a running counter would leak how many
        // certificates the platform has issued.
        val suffix = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        return "ELP-$year-$suffix"
    }

    private fun newVerificationCode(): String {
        val bytes = ByteArray(CODE_BYTES).also { RANDOM.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private companion object {
        val RANDOM = SecureRandom()
        const val CODE_BYTES = 24
    }
}

data class CertificateVerification(
    val certificateNumber: String,
    val courseId: UUID,
    val issuedAt: java.time.Instant,
    val valid: Boolean,
    val revokedAt: java.time.Instant?,
)
