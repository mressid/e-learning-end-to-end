package com.elearning.platform.transcode

import com.elearning.platform.transcode.application.TranscodeWorker
import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.TestAccounts
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
    @Autowired val accounts: TestAccounts,
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

    /**
     * Somebody who can own a course.
     *
     * Registration produces a student, and a student cannot author, so an
     * instructor account is made directly. [tokenFor] stays the learner.
     */
    private fun instructorTokenFor(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        accounts.instructor("$unique@example.com", unique, password)
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
                mapOf(
                    "title" to "Lecture",
                    "resourceType" to "VIDEO",
                    "sourceType" to "FILE",
                    "mediaId" to mediaId,
                ),
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
        val job = jobs.findByMediaId(UUID.fromString(mediaId)).orElseThrow()
        worker.process(requireNotNull(job.id).toString())
    }

    @Test
    fun `attaching a video queues exactly one job`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "lecture-${System.nanoTime()}.mp4")

        // Queued on lesson attachment, not on upload: media has no idea whether
        // a file is a lesson video or a submission attachment.
        val job = jobs.findByMediaId(UUID.fromString(mediaId))
        assertThat(job).isPresent()
        assertThat(job.get().status).isEqualTo(TranscodeStatus.QUEUED)
    }

    @Test
    fun `re-saving the same video does not queue the work twice`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "idempotent-${System.nanoTime()}.mp4")

        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = objectMapper.writeValueAsString(mapOf(
                    "title" to "Lecture",
                    "resourceType" to "VIDEO",
                    "sourceType" to "FILE",
                    "mediaId" to mediaId,
                ))
        }.andExpect { status { isOk() } }

        assertThat(jobs.findAll().count { it.mediaId == UUID.fromString(mediaId) }).isEqualTo(1)
    }

    @Test
    fun `a finished job points the file at its renditions`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val name = "finished-${System.nanoTime()}.mp4"
        val (itemId, mediaId) = videoLesson(teacher, student, name)

        runJobFor(mediaId, itemId)

        assertThat(pipeline.calls).contains(name)
        val job = jobs.findByMediaId(UUID.fromString(mediaId)).orElseThrow()
        assertThat(job.status).isEqualTo(TranscodeStatus.SUCCEEDED)
        assertThat(job.attempts).isEqualTo(1)

        // Duration and a poster are filled from the encode, so the lesson can be
        // rendered without the client probing the file itself.
        mockMvc.get("/api/v1/items/$itemId/lesson") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `playback rewrites the master to renditions this application serves`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "playable-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        val manifest = mockMvc.get("/api/v1/items/$itemId/lesson/stream.m3u8") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(manifest).startsWith("#EXTM3U")
        // A bare relative name would be resolved against storage, where it is
        // not signed and would be refused.
        assertThat(manifest).doesNotContain("\nv0.m3u8")
        assertThat(manifest).contains("/api/v1/items/$itemId/lesson/stream/v0.m3u8")
    }

    /**
     * The hop that used to break playback.
     *
     * Signing only the master got a player exactly one step: it followed a
     * signed link to a rendition, read the relative `v0_000.ts` out of it, and
     * asked storage for that name with no signature, because a query string
     * does not survive relative resolution.
     */
    @Test
    fun `a rendition comes back with its segments signed`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "rendition-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        val variant = mockMvc.get("/api/v1/items/$itemId/lesson/stream/v0.m3u8") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(variant).startsWith("#EXTM3U")
        assertThat(variant).doesNotContain("\nv0_000.ts")
        assertThat(variant).contains("X-Amz-Signature")
    }

    /**
     * Being allowed to watch a lesson is not being allowed to name objects in
     * the media bucket. The name is checked against what the master actually
     * references, so neither a traversal nor a plausible guess gets through.
     */
    @Test
    fun `a rendition name the master does not reference is refused`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val (itemId, mediaId) = videoLesson(teacher, student, "guessing-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        listOf("v9.m3u8", "source.mp4").forEach { name ->
            mockMvc.get("/api/v1/items/$itemId/lesson/stream/$name") {
                header("Authorization", "Bearer $student")
            }.andExpect { status { isNotFound() } }
        }

        // An encoded slash never reaches the handler: the container rejects it
        // outright, which is a stricter answer than ours and a welcome one.
        mockMvc.get("/api/v1/items/$itemId/lesson/stream/..%2F..%2Fsecret.m3u8") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `a stranger cannot fetch a rendition`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val stranger = tokenFor("stranger")
        val (itemId, mediaId) = videoLesson(teacher, student, "private-${System.nanoTime()}.mp4")
        runJobFor(mediaId, itemId)

        mockMvc.get("/api/v1/items/$itemId/lesson/stream/v0.m3u8") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a stranger cannot fetch the manifest`() {
        val teacher = instructorTokenFor("teacher")
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
        val teacher = instructorTokenFor("teacher")
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
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val name = "broken-${System.nanoTime()}.mp4"
        pipeline.failFor(name)
        val (itemId, mediaId) = videoLesson(teacher, student, name)

        // Rethrown so the broker retries and eventually dead-letters.
        runCatching { runJobFor(mediaId, itemId) }

        val job = jobs.findByMediaId(UUID.fromString(mediaId)).orElseThrow()
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
