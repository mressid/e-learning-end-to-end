package com.elearning.assessment

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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Quiz authoring, taking and grading.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuizApiTest(
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

    private fun array(node: JsonNode): List<JsonNode> = (0 until node.size()).map { node.get(it) }

    private data class Fixture(val teacher: String, val courseId: String, val quizItemId: String)

    /** A published course whose single required item is a quiz. */
    private fun quizCourse(quizJson: String = """{"title":"Quiz"}"""): Fixture {
        val teacher = instructorTokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val quizItemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"Q","type":"QUIZ"}"""))

        mockMvc.put("/api/v1/items/$quizItemId/quiz") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = quizJson
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Fixture(teacher, courseId, quizItemId)
    }

    private fun addQuestion(f: Fixture, json: String): String =
        id(postJson("/api/v1/items/${f.quizItemId}/quiz/questions", f.teacher, json))

    private fun singleChoice(f: Fixture, text: String = "2 + 2?") = addQuestion(
        f,
        """{"type":"SINGLE_CHOICE","text":"$text","points":10,
            "options":[{"text":"3","isCorrect":false},{"text":"4","isCorrect":true}]}""",
    )

    private fun enrolledStudent(f: Fixture): String {
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${f.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }
        return student
    }

    private fun startAttempt(token: String, f: Fixture): JsonNode {
        val body = mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/attempts") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body)
    }

    private fun submit(token: String, attemptId: String, answers: String) =
        mockMvc.post("/api/v1/attempts/$attemptId/submit") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"answers":$answers}"""
        }

    // ---- authoring -------------------------------------------------------

    @Test
    fun `a stranger cannot author a quiz`() {
        val f = quizCourse()
        val stranger = tokenFor("stranger")
        mockMvc.put("/api/v1/items/${f.quizItemId}/quiz") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $stranger")
            content = """{"title":"Hijack"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }

    @Test
    fun `a choice question needs options and at least one correct answer`() {
        val f = quizCourse()
        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/questions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"type":"SINGLE_CHOICE","text":"?","options":[]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("OPTIONS_REQUIRED") }
        }

        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/questions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"type":"SINGLE_CHOICE","text":"?",
                "options":[{"text":"a","isCorrect":false},{"text":"b","isCorrect":false}]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NO_CORRECT_OPTION") }
        }
    }

    @Test
    fun `a single-choice question cannot have two correct options`() {
        val f = quizCourse()
        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/questions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"type":"SINGLE_CHOICE","text":"?",
                "options":[{"text":"a","isCorrect":true},{"text":"b","isCorrect":true}]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("TOO_MANY_CORRECT_OPTIONS") }
        }
    }

    @Test
    fun `a text question cannot carry options`() {
        val f = quizCourse()
        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/questions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${f.teacher}")
            content = """{"type":"SHORT_TEXT","text":"?","options":[{"text":"a","isCorrect":true}]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("OPTIONS_NOT_ALLOWED") }
        }
    }

    // ---- the answer key must never reach a student -----------------------

    @Test
    fun `an attempt never exposes which option is correct`() {
        val f = quizCourse()
        singleChoice(f)
        val student = enrolledStudent(f)

        val body = mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/attempts") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        // Not merely "isCorrect is false" - the field must be absent entirely.
        assertThat(body).doesNotContain("isCorrect")
        val options = array(objectMapper.readTree(body).get("questions").get(0).get("options"))
        assertThat(options).hasSize(2)
        options.forEach { assertThat(it.get("isCorrect")).isNull() }
    }

    @Test
    fun `students cannot read the author's question list`() {
        val f = quizCourse()
        singleChoice(f)
        val student = enrolledStudent(f)

        mockMvc.get("/api/v1/items/${f.quizItemId}/quiz/questions") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }

    // ---- taking and grading ---------------------------------------------

    @Test
    fun `a student who is not enrolled cannot start an attempt`() {
        val f = quizCourse()
        singleChoice(f)
        val stranger = tokenFor("stranger")

        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/attempts") {
            header("Authorization", "Bearer $stranger")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_ENROLLED") }
        }
    }

    @Test
    fun `an empty quiz cannot be attempted`() {
        val f = quizCourse()
        val student = enrolledStudent(f)
        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/attempts") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("QUIZ_HAS_NO_QUESTIONS") }
        }
    }

    @Test
    fun `a correct answer scores 100 and completes the course item`() {
        val f = quizCourse("""{"title":"Quiz","passingScore":50}""")
        singleChoice(f)
        val student = enrolledStudent(f)

        val attempt = startAttempt(student, f)
        val attemptId = attempt.get("attemptId").asString()
        val question = attempt.get("questions").get(0)
        val correctOptionId = array(question.get("options")).first { it.get("text").asString() == "4" }
            .get("id").asString()

        submit(student, attemptId, """[{"questionId":"${question.get("id").asString()}",
            "selectedOptionIds":["$correctOptionId"]}]""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("GRADED") }
            jsonPath("$.score") { value(100.00) }
            jsonPath("$.passed") { value(true) }
            jsonPath("$.awaitingManualGrading") { value(false) }
        }

        // Passing the quiz completes the item it belongs to.
        mockMvc.get("/api/v1/courses/${f.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.completedItems") { value(1) }
            jsonPath("$.isComplete") { value(true) }
        }
    }

    @Test
    fun `a wrong answer scores zero and fails`() {
        val f = quizCourse("""{"title":"Quiz","passingScore":50}""")
        singleChoice(f)
        val student = enrolledStudent(f)

        val attempt = startAttempt(student, f)
        val question = attempt.get("questions").get(0)
        val wrongOptionId = array(question.get("options")).first { it.get("text").asString() == "3" }
            .get("id").asString()

        submit(
            student,
            attempt.get("attemptId").asString(),
            """[{"questionId":"${question.get("id").asString()}","selectedOptionIds":["$wrongOptionId"]}]""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.score") { value(0.00) }
            jsonPath("$.passed") { value(false) }
        }

        mockMvc.get("/api/v1/courses/${f.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect { jsonPath("$.completedItems") { value(0) } }
    }

    @Test
    fun `multiple choice needs every correct option and no wrong ones`() {
        val f = quizCourse()
        addQuestion(
            f,
            """{"type":"MULTIPLE_CHOICE","text":"Even numbers?","points":10,
                "options":[{"text":"1","isCorrect":false},{"text":"2","isCorrect":true},
                           {"text":"3","isCorrect":false},{"text":"4","isCorrect":true}]}""",
        )
        val student = enrolledStudent(f)

        val attempt = startAttempt(student, f)
        val question = attempt.get("questions").get(0)
        val questionId = question.get("id").asString()
        val byText = array(question.get("options")).associate { it.get("text").asString() to it.get("id").asString() }

        // Only one of the two correct options: no partial credit.
        submit(
            student,
            attempt.get("attemptId").asString(),
            """[{"questionId":"$questionId","selectedOptionIds":["${byText["2"]}"]}]""",
        ).andExpect { jsonPath("$.score") { value(0.00) } }

        val second = startAttempt(student, f)
        val secondQuestion = second.get("questions").get(0)
        val secondByText = array(secondQuestion.get("options"))
            .associate { it.get("text").asString() to it.get("id").asString() }
        submit(
            student,
            second.get("attemptId").asString(),
            """[{"questionId":"${secondQuestion.get("id").asString()}",
                "selectedOptionIds":["${secondByText["2"]}","${secondByText["4"]}"]}]""",
        ).andExpect { jsonPath("$.score") { value(100.00) } }
    }

    @Test
    fun `a free-text question leaves the attempt awaiting a human`() {
        val f = quizCourse()
        addQuestion(f, """{"type":"SHORT_TEXT","text":"Explain closures","points":10}""")
        val student = enrolledStudent(f)

        val attempt = startAttempt(student, f)
        submit(
            student,
            attempt.get("attemptId").asString(),
            """[{"questionId":"${attempt.get("questions").get(0).get("id").asString()}",
                "answerText":"a function plus its environment"}]""",
        ).andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUBMITTED") }
            // Scoring now would report a mark that ignored the written answer.
            jsonPath("$.score") { doesNotExist() }
            jsonPath("$.awaitingManualGrading") { value(true) }
        }
    }

    @Test
    fun `max attempts is enforced`() {
        val f = quizCourse("""{"title":"Quiz","maxAttempts":1}""")
        singleChoice(f)
        val student = enrolledStudent(f)

        val attempt = startAttempt(student, f)
        val question = attempt.get("questions").get(0)
        submit(
            student,
            attempt.get("attemptId").asString(),
            """[{"questionId":"${question.get("id").asString()}","selectedOptionIds":[]}]""",
        ).andExpect { status { isOk() } }

        mockMvc.post("/api/v1/items/${f.quizItemId}/quiz/attempts") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NO_ATTEMPTS_LEFT") }
        }
    }

    @Test
    fun `starting again resumes the attempt in progress instead of consuming one`() {
        val f = quizCourse("""{"title":"Quiz","maxAttempts":1}""")
        singleChoice(f)
        val student = enrolledStudent(f)

        val first = startAttempt(student, f)
        val second = startAttempt(student, f)
        assertThat(second.get("attemptId").asString()).isEqualTo(first.get("attemptId").asString())
    }

    @Test
    fun `an attempt cannot be submitted twice`() {
        val f = quizCourse()
        singleChoice(f)
        val student = enrolledStudent(f)
        val attempt = startAttempt(student, f)
        val attemptId = attempt.get("attemptId").asString()
        val questionId = attempt.get("questions").get(0).get("id").asString()

        submit(student, attemptId, """[{"questionId":"$questionId","selectedOptionIds":[]}]""")
            .andExpect { status { isOk() } }
        submit(student, attemptId, """[{"questionId":"$questionId","selectedOptionIds":[]}]""")
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.code") { value("ATTEMPT_CLOSED") }
            }
    }

    @Test
    fun `nobody can read or submit someone else's attempt`() {
        val f = quizCourse()
        singleChoice(f)
        val student = enrolledStudent(f)
        val other = enrolledStudent(f)
        val attemptId = startAttempt(student, f).get("attemptId").asString()

        mockMvc.get("/api/v1/attempts/$attemptId") {
            header("Authorization", "Bearer $other")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ATTEMPT_ACCESS_DENIED") }
        }
        submit(other, attemptId, "[]").andExpect { status { isForbidden() } }
    }

    @Test
    fun `an answer for a question outside the quiz is rejected`() {
        val f = quizCourse()
        singleChoice(f)
        val student = enrolledStudent(f)
        val attemptId = startAttempt(student, f).get("attemptId").asString()

        submit(
            student,
            attemptId,
            """[{"questionId":"00000000-0000-0000-0000-000000000000","selectedOptionIds":[]}]""",
        ).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("UNKNOWN_QUESTION") }
        }
    }

    @Test
    fun `a lesson item cannot be turned into a quiz`() {
        val teacher = instructorTokenFor("teacher")
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val lessonId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"L","type":"LESSON"}"""))

        mockMvc.put("/api/v1/items/$lessonId/quiz") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"title":"nope"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_A_QUIZ_ITEM") }
        }
    }
}
