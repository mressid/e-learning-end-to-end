package com.elearning.assessment.infrastructure

import com.elearning.platform.media.MediaReferenceProbe
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Assessment's answer: a student attached the file to work they handed in.
 *
 * `submission_media` cascades rather than nulling, so deleting the file would
 * detach it from the submission without trace - the marker would see an answer
 * that used to have an attachment and no sign one ever existed.
 */
@Component
class SubmissionMediaReferenceProbe(
    private val submissionMedia: SubmissionMediaRepository,
) : MediaReferenceProbe {
    override fun isReferenced(mediaId: UUID): Boolean = submissionMedia.existsByIdMediaId(mediaId)
    override fun describe(): String = "an assignment submission"
}
