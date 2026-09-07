package com.elearning.platform.transcode.application

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "elearning.playback")
data class PlaybackProperties(
    /**
     * How long signed segment URLs last.
     *
     * Bounds how useful a copied manifest is to somebody who never enrolled,
     * while comfortably outlasting the longest lecture anyone is likely to sit
     * through in one go.
     */
    val urlTtl: Duration = Duration.ofHours(4),
)
