package com.elearning.platform.media

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

/**
 * S3-compatible object storage: MinIO locally, S3 in production.
 *
 * Validated at startup so a missing endpoint or key fails immediately with a
 * clear message rather than at the first upload.
 */
@Validated
@ConfigurationProperties(prefix = "elearning.storage")
data class StorageProperties(
    @field:NotBlank val endpoint: String = "",
    @field:NotBlank val region: String = "us-east-1",
    @field:NotBlank val accessKey: String = "",
    @field:NotBlank val secretKey: String = "",
    /** MinIO requires path-style addressing; real S3 does not. */
    val pathStyleAccess: Boolean = true,
    /** Private bucket: everything reached through presigned URLs. */
    @field:NotBlank val mediaBucket: String = "elearning",
    /** Public bucket: thumbnails and other freely readable assets. */
    @field:NotBlank val publicBucket: String = "elearning-public",
    val presignedUrlTtl: Duration = Duration.ofMinutes(15),
)
