package com.elearning.platform.transcode.infrastructure

import com.elearning.platform.transcode.application.TranscodeFailedException
import com.elearning.platform.transcode.application.TranscodeResult
import com.elearning.platform.transcode.application.VideoPipeline
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Stands in where no encoder is configured - the API image, which has no ffmpeg
 * binary and never runs a job.
 *
 * Exists so the application context still starts. Without it every process that
 * is not a worker fails to come up at all, because the job machinery depends on
 * a pipeline being present.
 *
 * Selected by the same property that selects the real one, inverted - the pair
 * is mutually exclusive the way `SmtpEmailSender` and `MailgunEmailSender` are.
 * `@ConditionalOnMissingBean` would have read better but is only dependable on
 * auto-configuration classes, not on component-scanned ones.
 *
 * It throws rather than returning empty renditions: a job that silently
 * "succeeded" with nothing to play would mark the lesson as transcoded and
 * leave students with a manifest referencing no segments.
 */
@Component
@ConditionalOnProperty(
    name = ["elearning.transcode.enabled"],
    havingValue = "false",
    matchIfMissing = true,
)
class DisabledVideoPipeline : VideoPipeline {

    override fun transcode(source: ByteArray, sourceFilename: String?): TranscodeResult =
        throw TranscodeFailedException(
            "No encoder is configured in this process. Transcoding runs in the worker " +
                "(Dockerfile.worker), which sets elearning.transcode.enabled=true.",
        )
}
