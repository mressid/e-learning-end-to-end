package com.elearning.learning

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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI

/**
 * Reusable resources: creation per source type, attachment at the three scopes,
 * and who may read them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResourceApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
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

    private fun postJson(url: String, token: String, json: String): String =
        mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

    private fun id(json: String) = objectMapper.readTree(json).get("id").asString()
    private fun array(n: JsonNode): List<JsonNode> = (0 until n.size()).map { n.get(it) }

    private data class Course(val teacher: String, val courseId: String, val sectionId: String, val itemId: String)

    private fun publishedCourse(): Course {
        val teacher = tokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"L","type":"LESSON"}"""))
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Course(teacher, courseId, sectionId, itemId)
    }

    private fun enrolledStudent(c: Course): String {
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${c.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }
        return student
    }

    private fun uploadFile(token: String, body: String): String {
        val ticket = postJson(
            "/api/v1/media/uploads",
            token,
            """{"filename":"cheatsheet.pdf","contentType":"text/plain"}""",
        )
        val node = objectMapper.readTree(ticket)
        val mediaId = node.get("mediaId").asString()
        (URI.create(node.get("uploadUrl").asString()).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            outputStream.use { it.write(body.toByteArray()) }
            check(responseCode == 200)
            disconnect()
        }
        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        return mediaId
    }

    private fun inlineResource(token: String, title: String = "Git Commands"): String = id(
        postJson(
            "/api/v1/resources",
            token,
            """{"title":"$title","resourceType":"DOCUMENT","sourceType":"INLINE",
                "content":"# git\n- git status","contentType":"MARKDOWN"}""",
        ),
    )

    @Test
    fun `each source type requires its own content`() {
        val token = tokenFor("author")
        val cases = mapOf(
            """{"title":"t","resourceType":"DOCUMENT","sourceType":"FILE"}""" to "MEDIA_REQUIRED",
            """{"title":"t","resourceType":"LINK","sourceType":"URL"}""" to "URL_REQUIRED",
            """{"title":"t","resourceType":"DOCUMENT","sourceType":"INLINE"}""" to "CONTENT_REQUIRED",
        )
        cases.forEach { (body, code) ->
            mockMvc.post("/api/v1/resources") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $token")
                content = body
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.code") { value(code) }
            }
        }
    }

    @Test
    fun `only http and https urls are accepted`() {
        val token = tokenFor("author")
        // A javascript: link would be handed straight to a student's browser.
        mockMvc.post("/api/v1/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"bad","resourceType":"LINK","sourceType":"URL",
                "url":"javascript:alert(document.cookie)"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_URL") }
        }

        mockMvc.post("/api/v1/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"ok","resourceType":"LINK","sourceType":"URL","url":"https://docs.python.org"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `a file resource copies its metadata from the upload`() {
        val token = tokenFor("author")
        val mediaId = uploadFile(token, "cheat sheet body")

        val body = postJson(
            "/api/v1/resources",
            token,
            """{"title":"Cheat sheet","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""",
        )
        val node = objectMapper.readTree(body)
        assertThat(node.get("filename").asString()).isEqualTo("cheatsheet.pdf")
        assertThat(node.get("sizeBytes").asLong()).isEqualTo("cheat sheet body".length.toLong())
    }

    @Test
    fun `somebody else's upload cannot be turned into a resource`() {
        val author = tokenFor("author")
        val stranger = tokenFor("stranger")
        val mediaId = uploadFile(stranger, "not yours")

        mockMvc.post("/api/v1/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $author")
            content = """{"title":"t","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MEDIA_ACCESS_DENIED") }
        }
    }

    @Test
    fun `a resource attaches at course, section and item scope`() {
        val course = publishedCourse()
        val resourceId = inlineResource(course.teacher)

        listOf(
            "/api/v1/courses/${course.courseId}/resources",
            "/api/v1/sections/${course.sectionId}/resources",
            "/api/v1/items/${course.itemId}/resources",
        ).forEach { url ->
            mockMvc.post(url) {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer ${course.teacher}")
                content = """{"resourceId":"$resourceId","relationshipType":"REQUIRED"}"""
            }.andExpect { status { isCreated() } }
        }

        val student = enrolledStudent(course)
        listOf(
            "/api/v1/courses/${course.courseId}/resources",
            "/api/v1/sections/${course.sectionId}/resources",
            "/api/v1/items/${course.itemId}/resources",
        ).forEach { url ->
            mockMvc.get(url) { header("Authorization", "Bearer $student") }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].relationshipType") { value("REQUIRED") }
                jsonPath("$[0].resource.id") { value(resourceId) }
            }
        }
    }

    @Test
    fun `attaching is for course staff only`() {
        val course = publishedCourse()
        val student = enrolledStudent(course)
        val resourceId = inlineResource(student)

        mockMvc.post("/api/v1/courses/${course.courseId}/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"resourceId":"$resourceId"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }

    @Test
    fun `attaching twice does not duplicate or reorder`() {
        val course = publishedCourse()
        val resourceId = inlineResource(course.teacher)
        repeat(2) {
            mockMvc.post("/api/v1/courses/${course.courseId}/resources") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer ${course.teacher}")
                content = """{"resourceId":"$resourceId"}"""
            }.andExpect { status { isCreated() } }
        }

        mockMvc.get("/api/v1/courses/${course.courseId}/resources") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `attachments keep their order`() {
        val course = publishedCourse()
        val first = inlineResource(course.teacher, "First")
        val second = inlineResource(course.teacher, "Second")
        listOf(first, second).forEach {
            mockMvc.post("/api/v1/courses/${course.courseId}/resources") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer ${course.teacher}")
                content = """{"resourceId":"$it"}"""
            }.andExpect { status { isCreated() } }
        }

        val body = mockMvc.get("/api/v1/courses/${course.courseId}/resources") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andReturn().response.contentAsString
        val titles = array(objectMapper.readTree(body)).map { it.get("resource").get("title").asString() }
        assertThat(titles).containsExactly("First", "Second")
    }

    @Test
    fun `a resource is not readable just because you know its id`() {
        val course = publishedCourse()
        val resourceId = inlineResource(course.teacher)
        mockMvc.post("/api/v1/courses/${course.courseId}/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${course.teacher}")
            content = """{"resourceId":"$resourceId"}"""
        }.andExpect { status { isCreated() } }

        val outsider = tokenFor("outsider")
        mockMvc.get("/api/v1/resources/$resourceId") {
            header("Authorization", "Bearer $outsider")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("RESOURCE_ACCESS_DENIED") }
        }

        // Enrolling in the course it hangs off is what grants access.
        val student = enrolledStudent(course)
        mockMvc.get("/api/v1/resources/$resourceId") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a file resource yields a working download url for a participant`() {
        val course = publishedCourse()
        val mediaId = uploadFile(course.teacher, "resource body text")
        val resourceId = id(
            postJson(
                "/api/v1/resources",
                course.teacher,
                """{"title":"Notes","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""",
            ),
        )
        mockMvc.post("/api/v1/items/${course.itemId}/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${course.teacher}")
            content = """{"resourceId":"$resourceId"}"""
        }.andExpect { status { isCreated() } }

        val student = enrolledStudent(course)
        val body = mockMvc.get("/api/v1/resources/$resourceId/download-url") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val url = objectMapper.readTree(body).get("downloadUrl").asString()
        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        val text = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        assertThat(text).isEqualTo("resource body text")
    }

    @Test
    fun `an inline resource has no file to download`() {
        val course = publishedCourse()
        val resourceId = inlineResource(course.teacher)
        mockMvc.get("/api/v1/resources/$resourceId/download-url") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_A_FILE_RESOURCE") }
        }
    }

    @Test
    fun `detaching leaves the resource itself intact`() {
        val course = publishedCourse()
        val resourceId = inlineResource(course.teacher)
        mockMvc.post("/api/v1/courses/${course.courseId}/resources") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${course.teacher}")
            content = """{"resourceId":"$resourceId"}"""
        }.andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/courses/${course.courseId}/resources/$resourceId") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/courses/${course.courseId}/resources") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { jsonPath("$.length()") { value(0) } }

        // The creator can still reach it; only the attachment went away.
        mockMvc.get("/api/v1/resources/$resourceId") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `resources require authentication`() {
        mockMvc.get("/api/v1/resources/00000000-0000-0000-0000-000000000000")
            .andExpect { status { isUnauthorized() } }
    }
}
