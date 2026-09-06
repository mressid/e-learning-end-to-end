package com.elearning.learning.infrastructure

import com.elearning.platform.media.MediaReferenceProbe
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Learning's answer: the file is lesson content, a certificate document, or a
 * resource attachment.
 *
 * Certificates matter most here. A certificate's PDF is the credential itself,
 * and an employer may check it years later - deleting the file would leave a
 * verification that resolves to nothing.
 */
@Component
class LearningMediaReferenceProbe(
    private val videos: VideoContentRepository,
    private val documents: DocumentContentRepository,
    private val certificates: CertificateRepository,
    private val resourceFiles: ResourceFileRepository,
) : MediaReferenceProbe {

    override fun isReferenced(mediaId: UUID): Boolean =
        videos.existsByMediaId(mediaId) ||
            videos.existsByThumbnailMediaId(mediaId) ||
            videos.existsByHlsManifestMediaId(mediaId) ||
            documents.existsByMediaId(mediaId) ||
            certificates.existsByMediaId(mediaId) ||
            resourceFiles.existsByMediaId(mediaId)

    override fun describe(): String = "lesson content, a certificate or a resource"
}
