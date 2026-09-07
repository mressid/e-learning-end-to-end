package com.elearning.platform.transcode.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "elearning.transcode")
data class TranscodeProperties(
    /**
     * Off in the API, on in the worker.
     *
     * The API image carries no ffmpeg binary, so publishing the pipeline bean
     * there would fail at the first job rather than at startup.
     */
    val enabled: Boolean = false,

    val ffmpegPath: String = "ffmpeg",
    val ffprobePath: String = "ffprobe",

    /**
     * How long one video may take.
     *
     * A hung encode holds a worker and a broker delivery open indefinitely, so
     * it is killed and dead-lettered rather than left to occupy a slot.
     */
    val timeout: Duration = Duration.ofMinutes(30),

    /**
     * `veryfast` trades file size for encode time.
     *
     * A slower preset produces smaller files at the same quality, which is the
     * right trade when a video is watched thousands of times - but it can
     * triple the encode. Raise it once throughput stops being the constraint.
     */
    val preset: String = "veryfast",

    /**
     * Six seconds is the usual VOD compromise: short enough for a player to
     * switch rendition quickly on a bad connection, long enough that a
     * two-hour lecture is hundreds of objects rather than thousands.
     */
    val segmentSeconds: Int = 6,

    /** Far enough in to miss an opening fade or a black frame. */
    val posterOffsetSeconds: Int = 5,

    /** Renditions above the source's own height are skipped, never upscaled. */
    val ladder: List<Rung> = listOf(
        Rung(height = 1080, videoKbps = 5000, audioKbps = 128),
        Rung(height = 720, videoKbps = 2800, audioKbps = 128),
        Rung(height = 480, videoKbps = 1400, audioKbps = 96),
        Rung(height = 360, videoKbps = 800, audioKbps = 64),
    ),
) {
    data class Rung(val height: Int = 720, val videoKbps: Int = 2800, val audioKbps: Int = 128)
}
