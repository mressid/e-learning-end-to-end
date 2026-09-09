package com.elearning.learning

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.TestAccounts
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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI

/**
 * Lesson authoring and reading, including the rule that makes an uploaded file
 * reachable by an enrolled student without exposing its media id.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LessonApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val accounts: TestAccounts,
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

    private fun postJson(url: String, token: String, json: String): String =
        mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

    private fun id(json: String) = objectMapper.readTree(json).get("id").asString()

    private data class Course(val teacher: String, val courseId: String, val itemId: String)

    private fun publishedCourseWithItem(): Course {
        val teacher = instructorTokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(
            postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"L","type":"LESSON"}"""),
        )
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Course(teacher, courseId, itemId)
    }

    /** Uploads a real file to MinIO and returns its media id. */
    private fun uploadFile(token: String, body: String): String {
        val ticket = postJson(
            "/api/v1/media/uploads",
            token,
            """{"filename":"notes.txt","contentType":"text/plain"}""",
        )
        val node = objectMapper.readTree(ticket)
        val mediaId = node.get("mediaId").asString()
        val url = node.get("uploadUrl").asString()

        (URI.create(url).toURL().openConnection() as HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            outputStream.use { it.write(body.toByteArray()) }
            check(responseCode == 200) { "upload failed: $responseCode" }
            disconnect()
        }
        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        return mediaId
    }

    private fun saveLesson(token: String, itemId: String, json: String) =
        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }

    private fun enroll(token: String, courseId: String) =
        mockMvc.post("/api/v1/courses/$courseId/enroll") { header("Authorization", "Bearer $token") }
            .andExpect { status { isCreated() } }

    @Test
    fun `an editor saves an article lesson and reads it back`() {
        val course = publishedCourseWithItem()

        saveLesson(
            course.teacher,
            course.itemId,
            """{"title":"Chapter one","resourceType":"DOCUMENT","sourceType":"INLINE","content":"# Chapter one","description":"intro"}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.resourceType") { value("DOCUMENT") }
            jsonPath("$.sourceType") { value("INLINE") }
            jsonPath("$.contentFormat") { value("MARKDOWN") }
            jsonPath("$.content") { value("# Chapter one") }
            jsonPath("$.hasFile") { value(false) }
        }
    }

    @Test
    fun `saving is idempotent - the same item keeps one lesson`() {
        val course = publishedCourseWithItem()
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE","content":"first"}""")
            .andExpect { status { isOk() } }
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE","content":"second"}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.content") { value("second") }
            }
    }

    @Test
    fun `a stranger cannot author a lesson`() {
        val course = publishedCourseWithItem()
        val stranger = tokenFor("stranger")

        saveLesson(stranger, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE","content":"hijack"}""")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
            }
    }

    @Test
    fun `content is required for the declared source`() {
        val course = publishedCourseWithItem()

        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE"}""").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("CONTENT_REQUIRED") }
        }
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"VIDEO","sourceType":"FILE"}""").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("MEDIA_REQUIRED") }
        }
    }

    @Test
    fun `editing a file lesson keeps the file it already has`() {
        val course = publishedCourseWithItem()
        val mediaId = uploadFile(course.teacher, "slides")
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""")
            .andExpect { status { isOk() } }

        // The media id is never handed back, so an author changing only the
        // description has nothing to re-send. That must not cost them the file.
        saveLesson(
            course.teacher,
            course.itemId,
            """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","description":"Read this first"}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.hasFile") { value(true) }
            jsonPath("$.description") { value("Read this first") }
        }

        mockMvc.get("/api/v1/items/${course.itemId}/lesson/content-url") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { status { isOk() } }
    }

    /**
     * Audio and links used to be refused: the lesson had a name for them and no
     * table to put them in. A lesson's body is a resource now, and the resource
     * model always knew how to hold both.
     */
    @Test
    fun `a lesson can be an audio file or a link`() {
        val course = publishedCourseWithItem()
        val mediaId = uploadFile(course.teacher, "the recording")

        saveLesson(
            course.teacher,
            course.itemId,
            """{"title":"Episode one","resourceType":"AUDIO","sourceType":"FILE","mediaId":"$mediaId"}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.resourceType") { value("AUDIO") }
            jsonPath("$.sourceType") { value("FILE") }
            jsonPath("$.hasFile") { value(true) }
        }

        saveLesson(
            course.teacher,
            course.itemId,
            """{"title":"Read this","resourceType":"LINK","sourceType":"URL","url":"https://example.com/paper"}""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.sourceType") { value("URL") }
            jsonPath("$.url") { value("https://example.com/paper") }
            jsonPath("$.hasFile") { value(false) }
        }
    }

    @Test
    fun `a link lesson refuses a scheme a browser would run`() {
        val course = publishedCourseWithItem()
        saveLesson(
            course.teacher,
            course.itemId,
            """{"title":"L","resourceType":"LINK","sourceType":"URL","url":"javascript:alert(1)"}""",
        ).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_URL") }
        }
    }

    @Test
    fun `a lesson cannot point at an incomplete upload`() {
        val course = publishedCourseWithItem()
        val ticket = postJson(
            "/api/v1/media/uploads",
            course.teacher,
            """{"filename":"x.txt","contentType":"text/plain"}""",
        )
        val mediaId = objectMapper.readTree(ticket).get("mediaId").asString()

        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.code") { value("MEDIA_NOT_AVAILABLE") }
            }
    }

    @Test
    fun `an editor cannot attach a file uploaded by someone outside the course`() {
        val course = publishedCourseWithItem()
        val outsider = tokenFor("outsider")
        val theirMedia = uploadFile(outsider, "private")

        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$theirMedia"}""")
            .andExpect {
                status { isForbidden() }
                jsonPath("$.code") { value("MEDIA_ACCESS_DENIED") }
            }
    }

    @Test
    fun `an enrolled student reads the lesson and fetches its file without seeing the media id`() {
        val course = publishedCourseWithItem()
        val mediaId = uploadFile(course.teacher, "lecture notes body")
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""")
            .andExpect { status { isOk() } }

        val student = tokenFor("student")
        enroll(student, course.courseId)

        val body = mockMvc.get("/api/v1/items/${course.itemId}/lesson") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.hasFile") { value(true) }
        }.andReturn().response.contentAsString
        assertThat(body).doesNotContain(mediaId)

        val urlBody = mockMvc.get("/api/v1/items/${course.itemId}/lesson/content-url") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val url = objectMapper.readTree(urlBody).get("contentUrl").asString()

        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        val text = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        assertThat(text).isEqualTo("lecture notes body")
    }

    @Test
    fun `a student who is not enrolled cannot read the lesson or its file`() {
        val course = publishedCourseWithItem()
        val mediaId = uploadFile(course.teacher, "secret")
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"FILE","mediaId":"$mediaId"}""")
            .andExpect { status { isOk() } }

        val stranger = tokenFor("stranger")
        mockMvc.get("/api/v1/items/${course.itemId}/lesson") {
            header("Authorization", "Bearer $stranger")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_ENROLLED") }
        }
        mockMvc.get("/api/v1/items/${course.itemId}/lesson/content-url") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an article lesson has no file to download`() {
        val course = publishedCourseWithItem()
        saveLesson(course.teacher, course.itemId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE","content":"text"}""")
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/items/${course.itemId}/lesson/content-url") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("LESSON_HAS_NO_FILE") }
        }
    }

    @Test
    fun `a quiz item cannot be turned into a lesson`() {
        val teacher = instructorTokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val quizId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"Q","type":"QUIZ"}"""))

        saveLesson(teacher, quizId, """{"title":"L","resourceType":"DOCUMENT","sourceType":"INLINE","content":"nope"}""").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_A_LESSON_ITEM") }
        }
    }
}
