package com.elearning.learning.application

import com.elearning.learning.infrastructure.CertificateDetails
import com.elearning.learning.infrastructure.CertificatePdfRenderer
import com.elearning.learning.infrastructure.CertificateRepository
import com.elearning.platform.media.MediaService
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.events.CertificateIssued
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.net.URI
import java.util.UUID

/**
 * Renders the certificate PDF and attaches it to the certificate record.
 *
 * Runs AFTER_COMMIT: rendering and uploading to object storage would otherwise
 * hold the completion transaction open across a network call, and a storage
 * outage would roll back a course completion that genuinely happened. The
 * certificate is valid and verifiable without the PDF; the document catches up.
 */
@Service
class CertificateDocumentService(
    private val certificates: CertificateRepository,
    private val renderer: CertificatePdfRenderer,
    private val media: MediaService,
    private val catalog: CourseCatalog,
    private val learners: LearnerDirectory,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onCertificateIssued(event: CertificateIssued) {
        runCatching { generate(event.certificateId) }
            .onFailure { log.error("Could not render certificate {}", event.certificateNumber, it) }
    }

    /** Idempotent: a certificate that already has a document keeps it. */
    @Transactional
    fun generate(certificateId: UUID): UUID? {
        val certificate = certificates.findById(certificateId).orElse(null) ?: return null
        certificate.mediaId?.let { return it }

        val details = CertificateDetails(
            learnerName = learners.displayNameOf(certificate.studentId) ?: "Student",
            courseTitle = catalog.courseTitle(certificate.courseId) ?: "Course",
            certificateNumber = certificate.certificateNumber,
            verificationCode = certificate.verificationCode,
            issuedAt = certificate.issuedAt,
        )

        val stored = media.storeGenerated(
            filename = "${certificate.certificateNumber}.pdf",
            contentType = "application/pdf",
            bytes = renderer.render(details),
        )
        certificate.mediaId = stored.id
        return stored.id
    }

    /**
     * A download URL for the holder, or for staff of the course it certifies.
     * The media object has no uploader, so access is decided entirely here.
     */
    @Transactional(readOnly = true)
    fun downloadUrl(certificateId: UUID, requesterId: UUID): URI {
        val certificate = certificates.findById(certificateId)
            .orElseThrow { NotFoundException("CERTIFICATE_NOT_FOUND", "Certificate not found") }
        if (certificate.studentId != requesterId && !catalog.canEdit(certificate.courseId, requesterId)) {
            throw ForbiddenException("CERTIFICATE_ACCESS_DENIED", "This certificate is not yours")
        }
        val mediaId = certificate.mediaId
            ?: throw BusinessRuleException("CERTIFICATE_NOT_RENDERED", "The certificate document is not ready yet")
        return media.downloadUrlForAuthorizedCaller(mediaId)
    }
}
