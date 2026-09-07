package com.elearning.platform.media

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI

/**
 * Resumable uploads, against a real MinIO.
 *
 * Multipart semantics are exactly what a mock would get wrong - which parts
 * storage keeps, what an ETag has to be, whether an abort really discards. Only
 * the real thing answers those.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MultipartUploadApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    /** S3 refuses any part but the last below 5MB, so a real part it must be. */
    private val partSize = 5 * 1024 * 1024

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

    private fun begin(token: String, sizeBytes: Long, filename: String = "big.mp4") = objectMapper.readTree(
        mockMvc.post("/api/v1/media/uploads/multipart") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf("filename" to filename, "contentType" to "video/mp4", "sizeBytes" to sizeBytes),
            )
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
    )

    private fun partUrl(token: String, mediaId: String, part: Int): String = objectMapper.readTree(
        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts/$part") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("url").asString()

    /** Uploads one part straight to storage, exactly as a browser would. */
    private fun putPart(url: String, bytes: ByteArray): String {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(bytes.size)
        connection.outputStream.use { it.write(bytes) }
        check(connection.responseCode in 200..299) { "part upload failed: ${connection.responseCode}" }
        val etag = requireNotNull(connection.getHeaderField("ETag")) { "storage returned no ETag" }
        connection.disconnect()
        return etag
    }

    private fun listParts(token: String, mediaId: String) = objectMapper.readTree(
        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    @Test
    fun `a large upload is planned as parts that fit inside S3's limit`() {
        val token = tokenFor("planner")
        // 40GB: a fixed 5MB part would need 8,192 parts, and 100GB would exceed
        // the 10,000 ceiling entirely - so the size has to scale with the file.
        val ticket = begin(token, 40L * 1024 * 1024 * 1024)

        assertThat(ticket.get("uploadId").asString()).isNotBlank()
        assertThat(ticket.get("partCount").asInt()).isLessThanOrEqualTo(10_000)
        assertThat(ticket.get("partSizeBytes").asLong()).isGreaterThanOrEqualTo(5L * 1024 * 1024)
    }

    @Test
    fun `parts upload directly to storage and assemble into one object`() {
        val token = tokenFor("assembler")
        val ticket = begin(token, (partSize * 2).toLong())
        val mediaId = ticket.get("mediaId").asString()

        val first = ByteArray(partSize) { 1 }
        val second = ByteArray(1024) { 2 }
        val etag1 = putPart(partUrl(token, mediaId, 1), first)
        val etag2 = putPart(partUrl(token, mediaId, 2), second)

        val body = mockMvc.post("/api/v1/media/uploads/multipart/$mediaId/complete") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "parts" to listOf(
                        mapOf("partNumber" to 1, "etag" to etag1),
                        mapOf("partNumber" to 2, "etag" to etag2),
                    ),
                ),
            )
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val media = objectMapper.readTree(body)
        assertThat(media.get("status").asString()).isEqualTo("AVAILABLE")
        // Read back from storage, never taken from the client - the same rule
        // single-shot completion already follows.
        assertThat(media.get("sizeBytes").asLong()).isEqualTo((partSize + 1024).toLong())
    }

    @Test
    fun `listing parts is what makes an interrupted upload resumable`() {
        val token = tokenFor("resumer")
        val ticket = begin(token, (partSize * 3).toLong())
        val mediaId = ticket.get("mediaId").asString()

        // Two parts land, then the client "dies".
        putPart(partUrl(token, mediaId, 1), ByteArray(partSize) { 1 })
        putPart(partUrl(token, mediaId, 2), ByteArray(partSize) { 2 })

        // Coming back, it asks what storage kept rather than starting over.
        val parts = listParts(token, mediaId)
        assertThat(parts.size()).isEqualTo(2)
        assertThat((0 until parts.size()).map { parts.get(it).get("partNumber").asInt() })
            .isEqualTo(listOf(1, 2))
        assertThat(parts.get(0).get("etag").asString()).isNotBlank()

        // Only the missing part is sent.
        val etag3 = putPart(partUrl(token, mediaId, 3), ByteArray(512) { 3 })
        val etags = (0 until parts.size()).map { parts.get(it).get("etag").asString() }

        mockMvc.post("/api/v1/media/uploads/multipart/$mediaId/complete") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "parts" to listOf(
                        mapOf("partNumber" to 1, "etag" to etags[0]),
                        mapOf("partNumber" to 2, "etag" to etags[1]),
                        mapOf("partNumber" to 3, "etag" to etag3),
                    ),
                ),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.sizeBytes") { value((partSize * 2 + 512).toLong()) }
        }
    }

    @Test
    fun `aborting discards the parts storage was holding`() {
        val token = tokenFor("quitter")
        val ticket = begin(token, (partSize * 2).toLong())
        val mediaId = ticket.get("mediaId").asString()
        putPart(partUrl(token, mediaId, 1), ByteArray(partSize) { 1 })
        assertThat(listParts(token, mediaId).size()).isEqualTo(1)

        mockMvc.delete("/api/v1/media/uploads/multipart/$mediaId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        // The upload is gone, so it is no longer resumable - and the parts have
        // stopped costing anything.
        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_A_RESUMABLE_UPLOAD") }
        }
    }

    @Test
    fun `somebody else's upload is not yours to resume or finish`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val mediaId = begin(owner, (partSize * 2).toLong()).get("mediaId").asString()

        // A media id is a UUID, not a secret; the ownership check does the work.
        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }

        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts/1") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a finished upload cannot be resumed again`() {
        val token = tokenFor("finished")
        val mediaId = begin(token, 1024L).get("mediaId").asString()
        val etag = putPart(partUrl(token, mediaId, 1), ByteArray(512) { 9 })

        mockMvc.post("/api/v1/media/uploads/multipart/$mediaId/complete") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf("parts" to listOf(mapOf("partNumber" to 1, "etag" to etag))),
            )
        }.andExpect { status { isOk() } }

        // uploadId is cleared on completion, so nothing mistakes a finished
        // object for an upload still in flight - the sweeper included.
        mockMvc.get("/api/v1/media/uploads/multipart/$mediaId/parts") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_A_RESUMABLE_UPLOAD") }
        }
    }

    @Test
    fun `an upload with no size is refused, because the part plan depends on it`() {
        val token = tokenFor("sizeless")
        mockMvc.post("/api/v1/media/uploads/multipart") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"filename":"x.mp4","contentType":"video/mp4","sizeBytes":0}"""
        }.andExpect { status { isBadRequest() } }
    }
}
