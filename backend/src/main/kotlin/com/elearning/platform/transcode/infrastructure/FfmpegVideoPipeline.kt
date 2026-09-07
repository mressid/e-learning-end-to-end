package com.elearning.platform.transcode.infrastructure

import com.elearning.platform.transcode.application.TranscodeFailedException
import com.elearning.platform.transcode.application.TranscodeResult
import com.elearning.platform.transcode.application.VideoPipeline
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory

/**
 * The only class that knows FFmpeg exists.
 *
 * Everything else works in terms of [VideoPipeline], which is what makes a
 * managed encoder a swap rather than a rewrite - the same trick `EmailSender`
 * played when Mailgun arrived.
 *
 * Enabled by property so the API image, which has no ffmpeg binary, does not
 * publish a bean that would fail the moment it was used.
 */
@Component
@ConditionalOnProperty(name = ["elearning.transcode.enabled"], havingValue = "true")
class FfmpegVideoPipeline(private val properties: TranscodeProperties) : VideoPipeline {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun transcode(source: ByteArray, sourceFilename: String?): TranscodeResult {
        // FFmpeg works on files, and a lecture is far too large to pipe through
        // memory twice. The directory is removed in `finally` whatever happens,
        // because a worker that leaks a gigabyte per failed job fills its disk
        // in an afternoon.
        val work = createTempDirectory("transcode-").toFile()
        return try {
            val input = File(work, "source" + extensionOf(sourceFilename))
            input.writeBytes(source)

            val duration = probeDuration(input)
            val poster = extractPoster(work, input)
            val (manifest, segments) = renditionLadder(work, input)

            TranscodeResult(
                manifest = manifest,
                segments = segments,
                poster = poster,
                durationSeconds = duration,
            )
        } finally {
            work.deleteRecursively()
        }
    }

    /** Duration comes from ffprobe rather than being parsed out of ffmpeg's log. */
    private fun probeDuration(input: File): Int? {
        val output = run(
            listOf(
                properties.ffprobePath, "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.absolutePath,
            ),
            input.parentFile,
        )
        return output.trim().toDoubleOrNull()?.toInt()
    }

    /**
     * A frame from a little way in.
     *
     * Not from the first second: videos routinely open on black or a title
     * fade, and a black poster looks like a broken thumbnail rather than a
     * still.
     */
    private fun extractPoster(work: File, input: File): ByteArray? {
        val poster = File(work, "poster.jpg")
        return runCatching {
            run(
                listOf(
                    properties.ffmpegPath, "-y",
                    "-ss", properties.posterOffsetSeconds.toString(),
                    "-i", input.absolutePath,
                    "-frames:v", "1", "-q:v", "3",
                    poster.absolutePath,
                ),
                work,
            )
            poster.takeIf { it.exists() && it.length() > 0 }?.readBytes()
        }.onFailure {
            // A missing poster is cosmetic; the video still plays.
            log.warn("Could not extract a poster frame: {}", it.message)
        }.getOrNull()
    }

    /**
     * The adaptive ladder, and the compression.
     *
     * There is no separate "compress" step to add: re-encoding to these
     * bitrates *is* the compression. A rendition is only produced if the source
     * is at least that tall, because upscaling spends CPU to make a bigger file
     * that looks no better.
     */
    private fun renditionLadder(work: File, input: File): Pair<ByteArray, List<TranscodeResult.Segment>> {
        val height = probeHeight(input)
        val ladder: List<TranscodeProperties.Rung> =
            properties.ladder.filter { height == null || it.height <= height }
                .ifEmpty { listOf(properties.ladder.last()) }

        val args = mutableListOf(properties.ffmpegPath, "-y", "-i", input.absolutePath)
        val varStreamMap = ladder.indices.joinToString(" ") { "v:$it,a:$it" }

        ladder.forEachIndexed { index, rung ->
            args += listOf(
                "-map", "0:v:0", "-map", "0:a:0?",
                "-c:v:$index", "libx264", "-preset", properties.preset,
                "-b:v:$index", "${rung.videoKbps}k",
                "-maxrate:v:$index", "${(rung.videoKbps * 1.07).toInt()}k",
                "-bufsize:v:$index", "${rung.videoKbps * 2}k",
                "-filter:v:$index", "scale=-2:${rung.height}",
                "-c:a:$index", "aac", "-b:a:$index", "${rung.audioKbps}k",
            )
        }
        args += listOf(
            "-f", "hls",
            "-hls_time", properties.segmentSeconds.toString(),
            // One file per segment, and the whole list kept: this is video on
            // demand, so a sliding window would delete the beginning of the
            // lecture while the end was still encoding.
            "-hls_playlist_type", "vod",
            "-hls_segment_filename", File(work, "v%v_%03d.ts").absolutePath,
            "-master_pl_name", "index.m3u8",
            "-var_stream_map", varStreamMap,
            File(work, "v%v.m3u8").absolutePath,
        )

        run(args, work, properties.timeout.seconds)

        val master = File(work, "index.m3u8")
        if (!master.exists()) throw TranscodeFailedException("ffmpeg produced no master playlist")

        // Everything except the source and the poster is a segment or a variant
        // playlist, and both are addressed from the manifest by relative name.
        val segments = work.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name != "index.m3u8" && !it.name.startsWith("source") && it.name != "poster.jpg" }
            .map {
                TranscodeResult.Segment(
                    name = it.name,
                    bytes = it.readBytes(),
                    contentType = if (it.extension == "m3u8") {
                        "application/vnd.apple.mpegurl"
                    } else {
                        "video/mp2t"
                    },
                )
            }
        return master.readBytes() to segments
    }

    private fun probeHeight(input: File): Int? = runCatching {
        run(
            listOf(
                properties.ffprobePath, "-v", "error",
                "-select_streams", "v:0", "-show_entries", "stream=height",
                "-of", "default=noprint_wrappers=1:nokey=1",
                input.absolutePath,
            ),
            input.parentFile,
        ).trim().toIntOrNull()
    }.getOrNull()

    private fun run(command: List<String>, workingDir: File, timeoutSeconds: Long = 120): String {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw TranscodeFailedException("ffmpeg timed out after ${timeoutSeconds}s")
        }
        if (process.exitValue() != 0) {
            // FFmpeg's own last line says far more than the exit code does.
            throw TranscodeFailedException(
                "ffmpeg exited ${process.exitValue()}: ${output.lines().takeLast(3).joinToString(" ")}",
            )
        }
        return output
    }

    private fun extensionOf(filename: String?): String =
        filename?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
            ?.let { ".$it" } ?: ".mp4"

    private fun File.deleteRecursively() = Files.walk(toPath()).use { paths ->
        paths.sorted(Comparator.reverseOrder()).forEach { runCatching { Files.deleteIfExists(it) } }
    }
}
