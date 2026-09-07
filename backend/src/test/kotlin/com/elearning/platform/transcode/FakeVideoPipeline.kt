package com.elearning.platform.transcode

import com.elearning.platform.transcode.application.TranscodeFailedException
import com.elearning.platform.transcode.application.TranscodeResult
import com.elearning.platform.transcode.application.VideoPipeline
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentHashMap

/**
 * Stands in for FFmpeg.
 *
 * The binary is not on the CI image and a real encode takes minutes, so what is
 * verified here is everything *around* the encoder: the job lifecycle, the
 * queue, where renditions are stored, what gets written back to the lesson, and
 * that a failure degrades instead of breaking. That is the point of
 * [VideoPipeline] being a port.
 *
 * Failure is armed per call rather than by a global flag, for the reason the
 * recording mail sender already documents: the worker is asynchronous, so a job
 * from one test can be consumed after that test has finished and a global
 * switch would fail an unrelated one.
 */
class FakeVideoPipeline : VideoPipeline {

    private val failures = ConcurrentHashMap.newKeySet<String>()
    val calls = ConcurrentHashMap.newKeySet<String>()

    fun failFor(filename: String) {
        failures += filename
    }

    override fun transcode(source: ByteArray, sourceFilename: String?): TranscodeResult {
        val name = sourceFilename ?: "unnamed"
        calls += name
        if (name in failures) throw TranscodeFailedException("simulated encode failure for $name")

        return TranscodeResult(
            // A real master playlist references its variants by relative name,
            // which is exactly what PlaybackService has to rewrite.
            manifest = """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=2800000,RESOLUTION=1280x720
                v0.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=1400000,RESOLUTION=854x480
                v1.m3u8
            """.trimIndent().toByteArray(),
            segments = listOf(
                TranscodeResult.Segment("v0.m3u8", "#EXTM3U\nv0_000.ts".toByteArray(), "application/vnd.apple.mpegurl"),
                TranscodeResult.Segment("v1.m3u8", "#EXTM3U\nv1_000.ts".toByteArray(), "application/vnd.apple.mpegurl"),
                TranscodeResult.Segment("v0_000.ts", byteArrayOf(1, 2, 3), "video/mp2t"),
                TranscodeResult.Segment("v1_000.ts", byteArrayOf(4, 5, 6), "video/mp2t"),
            ),
            poster = byteArrayOf(7, 8, 9),
            durationSeconds = 754,
        )
    }
}

@TestConfiguration
class FakeVideoPipelineConfiguration {

    @Bean
    @Primary
    fun fakeVideoPipeline(): FakeVideoPipeline = FakeVideoPipeline()
}
