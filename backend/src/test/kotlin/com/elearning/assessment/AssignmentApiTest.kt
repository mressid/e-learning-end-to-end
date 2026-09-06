package com.elearning.assessment

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * Assignments end to end, plus the manual grading path that finishes a quiz
 * containing written answers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssignmentApiTest(
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

    private data class Fixture(val teacher: String, val courseId: String, val itemId: String)

    private fun courseWithItem(type: String): Fixture {
        val teacher = tokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"I","type":"$type"}"""))
        return Fixture(teacher, courseId, itemId)
    }

    private fun publish(f: Fixture) =
        mockMvc.post("/api/v1/courses/${f.courseId}/publish") { header("Authorization", "Bearer ${f.teacher}") }
            .andExpect { status { isOk() } }

    private fun saveAssignment(f: Fixture, json: String) =
        mockMvc.put("/api/v1/items/${f.itemId}/assignment") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = json
        }

    private fun enrolledStudent(f: Fixture): String {
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${f.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }
        return student
    }

    private fun uploadFile(token: String, body: String): String {
        val ticket = postJson(
            "/api/v1/media/uploads",
            token,
            """{"filename":"work.txt","contentType":"text/plain"}""",
        )
        val node = objectMapper.readTree(ticket)
        val mediaId = node.get("mediaId").asString()
        (URI.create(node.get("uploadUrl").asString()).toURL().openConnection() as HttpURLConnection).run {
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

    // ---- assignments -----------------------------------------------------

    @Test
    fun `a stranger cannot author an assignment`() {
        val f = courseWithItem("ASSIGNMENT")
        val stranger = tokenFor("stranger")
        mockMvc.put("/api/v1/items/${f.itemId}/assignment") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $stranger")
            content = """{"maxScore":100}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }

    @Test
    fun `a lesson item cannot be turned into an assignment`() {
        val f = courseWithItem("LESSON")
        saveAssignment(f, """{"maxScore":100}""").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_AN_ASSIGNMENT_ITEM") }
        }
    }

    @Test
    fun `an empty submission is rejected`() {
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":100}""").andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)

        mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("EMPTY_SUBMISSION") }
        }
    }

    @Test
    fun `late work is refused unless the assignment allows it`() {
        val past = Instant.now().minusSeconds(3600).toString()
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":100,"dueAt":"$past","allowLateSubmission":false}""")
            .andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)

        mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"late work"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("SUBMISSION_LATE") }
        }

        saveAssignment(f, """{"maxScore":100,"dueAt":"$past","allowLateSubmission":true}""")
            .andExpect { status { isOk() } }
        mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"late work"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `a student cannot submit somebody else's file`() {
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":100}""").andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)
        val otherStudent = enrolledStudent(f)
        val theirFile = uploadFile(otherStudent, "their work")

        mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"mine","mediaIds":["$theirFile"]}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("MEDIA_ACCESS_DENIED") }
        }
    }

    @Test
    fun `submitting then grading completes the item`() {
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":50,"instructions":"Build a REST API"}""")
            .andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)
        val file = uploadFile(student, "my solution")

        val submission = mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"see attached","mediaIds":["$file"]}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.attemptNumber") { value(1) }
            jsonPath("$.mediaIds.length()") { value(1) }
        }.andReturn().response.contentAsString
        val submissionId = objectMapper.readTree(submission).get("id").asString()

        // Handing in is progress, not completion.
        mockMvc.get("/api/v1/courses/${f.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect { jsonPath("$.completedItems") { value(0) } }

        mockMvc.post("/api/v1/submissions/$submissionId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"score":45,"feedback":"Solid work"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.score") { value(45) }
            jsonPath("$.feedback") { value("Solid work") }
        }

        mockMvc.get("/api/v1/courses/${f.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            jsonPath("$.completedItems") { value(1) }
            jsonPath("$.isComplete") { value(true) }
        }
    }

    @Test
    fun `a score above the maximum is rejected`() {
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":50}""").andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)
        val body = mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"work"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val submissionId = objectMapper.readTree(body).get("id").asString()

        mockMvc.post("/api/v1/submissions/$submissionId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"score":80}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_SCORE") }
        }
    }

    @Test
    fun `students cannot grade and cannot see the whole class's submissions`() {
        val f = courseWithItem("ASSIGNMENT")
        saveAssignment(f, """{"maxScore":100}""").andExpect { status { isOk() } }
        publish(f)
        val student = enrolledStudent(f)
        val body = mockMvc.post("/api/v1/items/${f.itemId}/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"work"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val submissionId = objectMapper.readTree(body).get("id").asString()

        mockMvc.post("/api/v1/submissions/$submissionId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"score":100}"""
        }.andExpect { status { isForbidden() } }

        mockMvc.get("/api/v1/items/${f.itemId}/assignment/submissions") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isForbidden() } }

        // Their own submissions remain visible to them.
        mockMvc.get("/api/v1/items/${f.itemId}/assignment/submissions/me") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    // ---- manual grading of written quiz answers --------------------------

    private fun quizWithTextQuestion(): Triple<Fixture, String, String> {
        val f = courseWithItem("QUIZ")
        mockMvc.put("/api/v1/items/${f.itemId}/quiz") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"title":"Mixed","passingScore":50}"""
        }.andExpect { status { isOk() } }

        postJson(
            "/api/v1/items/${f.itemId}/quiz/questions",
            f.teacher,
            """{"type":"SINGLE_CHOICE","text":"2+2?","points":10,
                "options":[{"text":"3","isCorrect":false},{"text":"4","isCorrect":true}]}""",
        )
        postJson(
            "/api/v1/items/${f.itemId}/quiz/questions",
            f.teacher,
            """{"type":"SHORT_TEXT","text":"Explain closures","points":10}""",
        )
        publish(f)
        val student = enrolledStudent(f)

        val attempt = objectMapper.readTree(
            postJson("/api/v1/items/${f.itemId}/quiz/attempts", student, ""),
        )
        return Triple(f, student, attempt.get("attemptId").asString())
    }

    @Test
    fun `a written answer is graded by hand and then completes the quiz`() {
        val (f, student, attemptId) = quizWithTextQuestion()

        val attempt = objectMapper.readTree(
            mockMvc.get("/api/v1/attempts/$attemptId") { header("Authorization", "Bearer $student") }
                .andReturn().response.contentAsString,
        )
        val questions = array(attempt.get("questions"))
        val choice = questions.first { it.get("type").asString() == "SINGLE_CHOICE" }
        val text = questions.first { it.get("type").asString() == "SHORT_TEXT" }
        val correctOption = array(choice.get("options")).first { it.get("text").asString() == "4" }
            .get("id").asString()

        mockMvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"answers":[
                {"questionId":"${choice.get("id").asString()}","selectedOptionIds":["$correctOption"]},
                {"questionId":"${text.get("id").asString()}","answerText":"a function plus its environment"}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            jsonPath("$.awaitingManualGrading") { value(true) }
        }

        // Only the written answer is listed; the choice question is already done.
        val grading = objectMapper.readTree(
            mockMvc.get("/api/v1/attempts/$attemptId/grading") {
                header("Authorization", "Bearer ${f.teacher}")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )
        val toGrade = array(grading.get("responses"))
        assertThat(toGrade).hasSize(1)
        assertThat(toGrade[0].get("answerText").asString()).isEqualTo("a function plus its environment")
        val responseId = toGrade[0].get("responseId").asString()

        mockMvc.post("/api/v1/attempts/$attemptId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"grades":[{"responseId":"$responseId","pointsAwarded":10}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GRADED") }
            // 10 of 10 written + 10 of 10 choice = the whole paper.
            jsonPath("$.score") { value(100.00) }
            jsonPath("$.passed") { value(true) }
        }

        mockMvc.get("/api/v1/courses/${f.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect { jsonPath("$.completedItems") { value(1) } }
    }

    @Test
    fun `grading is refused while any written answer is unmarked`() {
        val (f, student, attemptId) = quizWithTextQuestion()
        val attempt = objectMapper.readTree(
            mockMvc.get("/api/v1/attempts/$attemptId") { header("Authorization", "Bearer $student") }
                .andReturn().response.contentAsString,
        )
        val text = array(attempt.get("questions")).first { it.get("type").asString() == "SHORT_TEXT" }

        mockMvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"answers":[{"questionId":"${text.get("id").asString()}","answerText":"something"}]}"""
        }.andExpect { status { isOk() } }

        // No grades supplied at all.
        mockMvc.post("/api/v1/attempts/$attemptId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"grades":[]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("GRADING_INCOMPLETE") }
        }
    }

    @Test
    fun `manual grading notifies the student`() {
        val (f, student, attemptId) = quizWithTextQuestion()
        val attempt = objectMapper.readTree(
            mockMvc.get("/api/v1/attempts/$attemptId") { header("Authorization", "Bearer $student") }
                .andReturn().response.contentAsString,
        )
        val text = array(attempt.get("questions")).first { it.get("type").asString() == "SHORT_TEXT" }

        mockMvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"answers":[{"questionId":"${text.get("id").asString()}","answerText":"an answer"}]}"""
        }.andExpect { status { isOk() } }

        val responseId = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/attempts/$attemptId/grading") {
                    header("Authorization", "Bearer ${f.teacher}")
                }.andReturn().response.contentAsString,
            ).get("responses"),
        ).first().get("responseId").asString()

        mockMvc.post("/api/v1/attempts/$attemptId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"grades":[{"responseId":"$responseId","pointsAwarded":10}]}"""
        }.andExpect { status { isOk() } }

        // Marking happens long after the student left, so they must be told.
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val body = mockMvc.get("/api/v1/me/notifications") {
                header("Authorization", "Bearer $student")
            }.andReturn().response.contentAsString
            val types = array(objectMapper.readTree(body).get("content")).map { it.get("type").asString() }
            assertThat(types).contains("QUIZ_GRADED")
        }
    }

    @Test
    fun `a student cannot grade their own attempt`() {
        val (_, student, attemptId) = quizWithTextQuestion()
        mockMvc.get("/api/v1/attempts/$attemptId/grading") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }
}
