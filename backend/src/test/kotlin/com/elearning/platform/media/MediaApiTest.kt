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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI

/**
 * Exercises the real presigned upload/download cycle against MinIO. Mocking the
 * storage here would test nothing that matters: the whole point is that a URL
 * this service signs is one object storage actually accepts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MediaApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val mediaObjects: MediaObjectRepository,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun tokenFor(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("accessToken").asString()
    }

    private fun requestUpload(token: String, contentType: String = "text/plain"): Pair<String, String> {
        val body = mockMvc.post("/api/v1/media/uploads") {
            this.contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"filename":"Lecture 01 — Intro!.txt","contentType":"$contentType"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("PENDING") }
        }.andReturn().response.contentAsString
        val node = objectMapper.readTree(body)
        return node.get("mediaId").asString() to node.get("uploadUrl").asString()
    }

    /** PUTs bytes straight at storage, the way a browser would. */
    private fun putToStorage(url: String, body: ByteArray, contentType: String = "text/plain"): Int =
        (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", contentType)
            outputStream.use { it.write(body) }
            responseCode.also { disconnect() }
        }

    private fun getFromStorage(url: String): Pair<Int, String> =
        (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "GET"
            val code = responseCode
            val text = if (code < 400) inputStream.bufferedReader().readText() else ""
            disconnect()
            code to text
        }

    @Test
    fun `uploads and downloads a file without the bytes touching the API`() {
        val token = tokenFor("uploader")
        val (mediaId, uploadUrl) = requestUpload(token)
        val payload = "hello from the e-learning platform".toByteArray()

        assertThat(putToStorage(uploadUrl, payload)).isEqualTo(200)

        mockMvc.post("/api/v1/media/$mediaId/complete") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("AVAILABLE") }
            // Size is read back from storage, never taken from the client.
            jsonPath("$.sizeBytes") { value(payload.size) }
            // The original name survives even though the key is sanitised.
            jsonPath("$.originalFilename") { value("Lecture 01 — Intro!.txt") }
        }

        // The checksum is read back from storage too, not supplied by the client.
        val stored = mediaObjects.findById(java.util.UUID.fromString(mediaId)).orElseThrow()
        assertThat(stored.checksum).isNotBlank()

        val downloadBody = mockMvc.get("/api/v1/media/$mediaId/download-url") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val downloadUrl = objectMapper.readTree(downloadBody).get("downloadUrl").asString()

        val (code, text) = getFromStorage(downloadUrl)
        assertThat(code).isEqualTo(200)
        assertThat(text).isEqualTo(String(payload))
    }

    @Test
    fun `completing before anything was uploaded is rejected`() {
        val token = tokenFor("uploader")
        val (mediaId, _) = requestUpload(token)

        mockMvc.post("/api/v1/media/$mediaId/complete") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("UPLOAD_NOT_FOUND") }
        }
    }

    @Test
    fun `the presigned URL only accepts the declared content type`() {
        val token = tokenFor("uploader")
        val (_, uploadUrl) = requestUpload(token, contentType = "text/plain")

        // Content-Type is signed into the URL, so storage rejects a mismatch.
        assertThat(putToStorage(uploadUrl, "x".toByteArray(), contentType = "application/octet-stream"))
            .isEqualTo(403)
        assertThat(putToStorage(uploadUrl, "x".toByteArray(), contentType = "text/plain"))
            .isEqualTo(200)
    }

    @Test
    fun `the bucket is not readable without a signature`() {
        val token = tokenFor("uploader")
        val (mediaId, uploadUrl) = requestUpload(token)
        putToStorage(uploadUrl, "secret".toByteArray())
        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }

        val body = mockMvc.get("/api/v1/media/$mediaId/download-url") {
            header("Authorization", "Bearer $token")
        }.andReturn().response.contentAsString
        val signed = objectMapper.readTree(body).get("downloadUrl").asString()

        val (code, _) = getFromStorage(signed.substringBefore('?'))
        assertThat(code).isEqualTo(403)
    }

    @Test
    fun `another user cannot complete or download someone else's media`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val (mediaId, uploadUrl) = requestUpload(owner)
        putToStorage(uploadUrl, "mine".toByteArray())

        mockMvc.post("/api/v1/media/$mediaId/complete") {
            header("Authorization", "Bearer $stranger")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MEDIA_ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/media/$mediaId/download-url") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a download URL is refused while the upload is still pending`() {
        val token = tokenFor("uploader")
        val (mediaId, _) = requestUpload(token)

        mockMvc.get("/api/v1/media/$mediaId/download-url") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("MEDIA_NOT_AVAILABLE") }
        }
    }

    @Test
    fun `media endpoints require authentication`() {
        mockMvc.post("/api/v1/media/uploads") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"contentType":"text/plain"}"""
        }.andExpect { status { isUnauthorized() } }
    }
}
