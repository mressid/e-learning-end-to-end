package com.elearning.community

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Discussions and reviews: who may take part, and what stays private.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunityApiTest(
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

    private data class Fixture(val teacher: String, val courseId: String, val lessonItemId: String)

    private fun publishedCourse(): Fixture {
        val teacher = tokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"L","type":"LESSON"}"""))
        // A thread's lesson_id references `lessons`, so the item needs content.
        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"contentType":"ARTICLE","content":"body"}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Fixture(teacher, courseId, itemId)
    }

    private fun enrolledStudent(f: Fixture): String {
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${f.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }
        return student
    }

    // ---- discussions -----------------------------------------------------

    @Test
    fun `an enrolled student starts a thread and the teacher replies`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)

        val threadId = id(
            postJson(
                "/api/v1/courses/${f.courseId}/threads",
                student,
                """{"title":"Why does this fail?","body":"my code throws"}""",
            ),
        )

        postJson("/api/v1/threads/$threadId/comments", f.teacher, """{"body":"Check your imports"}""")

        mockMvc.get("/api/v1/threads/$threadId") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.thread.title") { value("Why does this fail?") }
            jsonPath("$.comments.length()") { value(1) }
            jsonPath("$.comments[0].body") { value("Check your imports") }
        }
    }

    @Test
    fun `outsiders cannot read or start threads`() {
        val f = publishedCourse()
        val outsider = tokenFor("outsider")

        mockMvc.get("/api/v1/courses/${f.courseId}/threads") {
            header("Authorization", "Bearer $outsider")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_A_PARTICIPANT") }
        }

        mockMvc.post("/api/v1/courses/${f.courseId}/threads") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $outsider")
            content = """{"title":"hello"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `anonymous callers cannot read threads even though course reads are public`() {
        val f = publishedCourse()
        // GET /courses/** is permitted anonymously for discovery, so the
        // participation rule has to hold at the application layer.
        mockMvc.get("/api/v1/courses/${f.courseId}/threads").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `a lesson thread must belong to the course it is filed under`() {
        val f = publishedCourse()
        val other = publishedCourse()
        val student = enrolledStudent(f)

        mockMvc.post("/api/v1/courses/${f.courseId}/threads") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"title":"t","lessonId":"${other.lessonItemId}"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("LESSON_NOT_IN_COURSE") }
        }
    }

    @Test
    fun `threads can be filtered to one lesson`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        postJson("/api/v1/courses/${f.courseId}/threads", student, """{"title":"course-wide"}""")
        postJson(
            "/api/v1/courses/${f.courseId}/threads",
            student,
            """{"title":"lesson-specific","lessonId":"${f.lessonItemId}"}""",
        )

        val body = mockMvc.get("/api/v1/courses/${f.courseId}/threads?lessonId=${f.lessonItemId}") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val content = objectMapper.readTree(body).get("content")
        assertThat(array(content)).hasSize(1)
        assertThat(content.get(0).get("title").asString()).isEqualTo("lesson-specific")
    }

    @Test
    fun `a closed thread takes no more replies`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        val threadId = id(postJson("/api/v1/courses/${f.courseId}/threads", student, """{"title":"t"}"""))

        mockMvc.post("/api/v1/threads/$threadId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"CLOSED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CLOSED") }
        }

        mockMvc.post("/api/v1/threads/$threadId/comments") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"body":"one more"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("THREAD_CLOSED") }
        }
    }

    @Test
    fun `only course staff can hide a thread`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        val threadId = id(postJson("/api/v1/courses/${f.courseId}/threads", student, """{"title":"t"}"""))

        // The author owns the thread but moderation is not theirs.
        mockMvc.post("/api/v1/threads/$threadId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"HIDDEN"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MODERATION_DENIED") }
        }

        mockMvc.post("/api/v1/threads/$threadId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"status":"HIDDEN"}"""
        }.andExpect { status { isOk() } }

        // Hidden threads drop out of the listing and out of the author's reach.
        val body = mockMvc.get("/api/v1/courses/${f.courseId}/threads") {
            header("Authorization", "Bearer $student")
        }.andReturn().response.contentAsString
        assertThat(array(objectMapper.readTree(body).get("content"))).isEmpty()

        mockMvc.get("/api/v1/threads/$threadId") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `a reply cannot be grafted onto a comment in another thread`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        val first = id(postJson("/api/v1/courses/${f.courseId}/threads", student, """{"title":"a"}"""))
        val second = id(postJson("/api/v1/courses/${f.courseId}/threads", student, """{"title":"b"}"""))
        val commentId = id(postJson("/api/v1/threads/$first/comments", student, """{"body":"hi"}"""))

        mockMvc.post("/api/v1/threads/$second/comments") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"body":"reply","parentId":"$commentId"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("PARENT_IN_OTHER_THREAD") }
        }
    }

    // ---- reviews ---------------------------------------------------------

    @Test
    fun `only someone who enrolled may review`() {
        val f = publishedCourse()
        val outsider = tokenFor("outsider")

        mockMvc.post("/api/v1/courses/${f.courseId}/reviews") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $outsider")
            content = """{"rating":5}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_ENROLLED") }
        }
    }

    @Test
    fun `an instructor cannot review their own course`() {
        val f = publishedCourse()
        mockMvc.post("/api/v1/courses/${f.courseId}/reviews") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"rating":5,"title":"Excellent, if I say so myself"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CANNOT_REVIEW_OWN_COURSE") }
        }
    }

    @Test
    fun `one review per student per course`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        postJson("/api/v1/courses/${f.courseId}/reviews", student, """{"rating":4,"title":"Good"}""")

        mockMvc.post("/api/v1/courses/${f.courseId}/reviews") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"rating":5}"""
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_REVIEWED") }
        }
    }

    @Test
    fun `a rating outside one to five is rejected`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        mockMvc.post("/api/v1/courses/${f.courseId}/reviews") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"rating":9}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }
    }

    @Test
    fun `a student edits their own review but not somebody else's`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        val other = enrolledStudent(f)
        val reviewId = id(postJson("/api/v1/courses/${f.courseId}/reviews", student, """{"rating":3}"""))

        mockMvc.patch("/api/v1/reviews/$reviewId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"rating":5,"title":"Changed my mind"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.rating") { value(5) }
        }

        mockMvc.patch("/api/v1/reviews/$reviewId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $other")
            content = """{"rating":1}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("REVIEW_ACCESS_DENIED") }
        }
    }

    @Test
    fun `published reviews are public and summarised`() {
        val f = publishedCourse()
        val a = enrolledStudent(f)
        val b = enrolledStudent(f)
        postJson("/api/v1/courses/${f.courseId}/reviews", a, """{"rating":4}""")
        postJson("/api/v1/courses/${f.courseId}/reviews", b, """{"rating":5}""")

        // No Authorization header at all.
        mockMvc.get("/api/v1/courses/${f.courseId}/reviews").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(2) }
        }
        mockMvc.get("/api/v1/courses/${f.courseId}/reviews/summary").andExpect {
            status { isOk() }
            jsonPath("$.total") { value(2) }
            jsonPath("$.average") { value(4.50) }
        }
    }

    @Test
    fun `a hidden review disappears from the public listing and the average`() {
        val f = publishedCourse()
        val a = enrolledStudent(f)
        val b = enrolledStudent(f)
        val hidden = id(postJson("/api/v1/courses/${f.courseId}/reviews", a, """{"rating":1}"""))
        postJson("/api/v1/courses/${f.courseId}/reviews", b, """{"rating":5}""")

        mockMvc.post("/api/v1/reviews/$hidden/moderate") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"status":"HIDDEN"}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses/${f.courseId}/reviews/summary").andExpect {
            jsonPath("$.total") { value(1) }
            jsonPath("$.average") { value(5.00) }
        }
    }

    @Test
    fun `students cannot moderate reviews`() {
        val f = publishedCourse()
        val student = enrolledStudent(f)
        val reviewId = id(postJson("/api/v1/courses/${f.courseId}/reviews", student, """{"rating":2}"""))

        mockMvc.post("/api/v1/reviews/$reviewId/moderate") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"PUBLISHED"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MODERATION_DENIED") }
        }
    }

    @Test
    fun `a course with no reviews summarises to no average`() {
        val f = publishedCourse()
        mockMvc.get("/api/v1/courses/${f.courseId}/reviews/summary").andExpect {
            status { isOk() }
            jsonPath("$.total") { value(0) }
            jsonPath("$.average") { doesNotExist() }
        }
    }
}
