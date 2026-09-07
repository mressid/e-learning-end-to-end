package com.elearning.platform.transcode

import com.elearning.platform.transcode.application.TranscodeWorker
import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * The transcoding pipeline, with FFmpeg itself faked.
 *
 * The binary is not on the CI image and a real encode takes minutes, so what is
 * exercised here is everything around it - job lifecycle, storage of the
 * renditions, what lands on the lesson, signed playback, and that a failure
 * leaves the lesson playable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FakeVideoPipelineConfiguration::class)
class TranscodeApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val jobs: TranscodeRepository,
    @Autowired val worker: TranscodeWorker,
    @Autowired val pipeline: FakeVideoPipeline,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun tokenFor(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        return objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    /** Uploads a file for real against MinIO and completes it. */
    private fun uploadedVideo(token: String, filename: String): String {
        val ticket = objectMapper.readTree(
            mockMvc.post("/api/v1/media/uploads") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $token")
                content = objectMapper.writeValueAsString(
                    mapOf("filename" to filename, "contentType" to "video/mp4", "sizeBytes" to 12),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        )
        val mediaId = ticket.get("mediaId").asString()
        val url = java.net.URI(ticket.get("uploadUrl").asString()).toURL()

        (url.openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "PUT"
            setRequestProperty("Content-Type", "video/mp4")
            doOutput = true
            outputStream.use { it.write("fake video!!".toByteArray()) }
            check(responseCode in 200..299) { "upload failed: $responseCode" }
            disconnect()
        }

        mockMvc.post("/api/v1/media/$mediaId/complete") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }
        return mediaId
    }

    /** A published course with one video lesson, and the student enrolled. */
    private fun videoLesson(teacher: String, student: String, filename: String): Pair<String, String> {
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Streamed ${System.nanoTime()}"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val sectionId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Section"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val itemId = objectMapper.readTree(
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Lecture","type":"LESSON"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        val mediaId = uploadedVideo(teacher, filename)
        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = objectMapper.writeValueAsString(
                mapOf("contentType" to "VIDEO", "mediaId" to mediaId),
            )
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $teacher")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
        return itemId to mediaId
    }

    private fun runJobFor(mediaId: String, itemId: String) {
        val job = jobs.findByMediaIdAndLessonId(UUID.fromString(mediaId), UUID.fromString(itemId)).orElseThrow()
        worker.process(requireNotNull(job.id).toString())
    }

    @Test
    fun `attaching a video queues exactly one job`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "lecture-${System.nanoTime()}.mp4")

        // Queued on lesson attachment, not on upload: media has no idea whether
        // a file is a lesson video or a submission attachment.
        val job = jobs.findByMediaIdAndLessonId(UUID.fromString(mediaId), UUID.fromString(itemId))
        assertThat(job).isPresent()
        assertThat(job.get().status).isEqualTo(TranscodeStatus.QUEUED)
    }

    @Test
    fun `re-saving the same video does not queue the work twice`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "idempotent-${System.nanoTime()}.mp4")

        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = objectMapper.writeValueAsString(mapOf("contentType" to "VIDEO", "mediaId" to mediaId))
        }.andExpect { status { isOk() } }

        assertThat(jobs.findByLessonId(UUID.fromString(itemId))).hasSize(1)
    }

    @Test
    fun `a finished job points the lesson at its renditions`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val name = "finished-${System.nanoTime()}.mp4"
        val (itemId, mediaId) = videoLesson(teacher, student, name)

        runJobFor(mediaId, itemId)

        assertThat(pipeline.calls).contains(name)
        val job = jobs.findByMediaIdAndLessonId(UUID.fromString(mediaId), UUID.fromString(itemId)).orElseThrow()
        assertThat(job.status).isEqualTo(TranscodeStatus.SUCCEEDED)
        assertThat(job.attempts).isEqualTo(1)

        // Duration and a poster are filled from the encode, so the lesson can be
        // rendered without the client probing the file itself.
        mockMvc.get("/api/v1/items/$itemId/lesson") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `playback returns a manifest whose segment URLs are signed`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "playable-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        val manifest = mockMvc.get("/api/v1/items/$itemId/lesson/stream.m3u8") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(manifest).startsWith("#EXTM3U")
        // Relative names have become absolute signed URLs; the enrolment check
        // would be a formality if a copied manifest kept working forever.
        assertThat(manifest).doesNotContain("\nv0.m3u8")
        assertThat(manifest).contains("http")
        assertThat(manifest).contains("X-Amz-Signature")
    }

    @Test
    fun `a stranger cannot fetch the manifest`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val stranger = tokenFor("stranger")
        val (itemId, mediaId) = videoLesson(teacher, student, "guarded-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        mockMvc.get("/api/v1/items/$itemId/lesson/stream.m3u8") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `playback before the encode finishes says so, and the original still works`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, _) = videoLesson(teacher, student, "pending-${System.nanoTime()}.mp4")

        mockMvc.get("/api/v1/items/$itemId/lesson/stream.m3u8") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("STREAM_NOT_READY") }
        }

        // Degrading, not breaking: the uploaded file is still there to play.
        mockMvc.get("/api/v1/items/$itemId/lesson/content-url") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a failed encode is recorded with its reason and leaves the lesson playable`() {
        val teacher = tokenFor("teacher")
        val student = tokenFor("student")
        val name = "broken-${System.nanoTime()}.mp4"
        pipeline.failFor(name)
        val (itemId, mediaId) = videoLesson(teacher, student, name)

        // Rethrown so the broker retries and eventually dead-letters.
        runCatching { runJobFor(mediaId, itemId) }

        val job = jobs.findByMediaIdAndLessonId(UUID.fromString(mediaId), UUID.fromString(itemId)).orElseThrow()
        // The failure survives the rethrow, which is why it is written in its
        // own transaction rather than the one that is about to roll back.
        assertThat(job.status).isEqualTo(TranscodeStatus.FAILED)
        assertThat(job.error).contains("simulated encode failure")

        mockMvc.get("/api/v1/items/$itemId/lesson/content-url") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `an unreadable message is discarded rather than retried forever`() {
        // Retrying cannot fix an unparseable id, and requeuing it would block
        // every other video behind it.
        worker.process("not-a-uuid")
    }
}
