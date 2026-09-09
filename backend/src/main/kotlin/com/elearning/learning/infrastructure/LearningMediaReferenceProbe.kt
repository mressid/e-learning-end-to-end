package com.elearning.learning.infrastructure

import com.elearning.platform.media.MediaReferenceProbe
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Learning's answer: the file is a certificate document or a resource.
 *
 * Lesson content used to be a third case. It is a resource now, so the resource
 * check below covers it and the lesson has no files of its own to ask about.
 *
 * Certificates matter most here. A certificate's PDF is the credential itself,
 * and an employer may check it years later - deleting the file would leave a
 * verification that resolves to nothing.
 */
@Component
class LearningMediaReferenceProbe(
    private val certificates: CertificateRepository,
    private val resourceFiles: ResourceFileRepository,
) : MediaReferenceProbe {

    override fun isReferenced(mediaId: UUID): Boolean =
        certificates.existsByMediaId(mediaId) || resourceFiles.existsByMediaId(mediaId)

    override fun describe(): String = "a certificate or a resource"
}
