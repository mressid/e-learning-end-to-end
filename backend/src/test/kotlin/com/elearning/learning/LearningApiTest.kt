package com.elearning.learning

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.TestAccounts
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

/**
 * Enrolment rules and the progress state machine.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LearningApiTest(
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

    private fun post(url: String, token: String, json: String): String =
        mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

    private fun id(json: String) = objectMapper.readTree(json).get("id").asString()

    /** A published course with [required] required items and [optional] optional ones. */
    private data class Fixture(val teacher: String, val courseId: String, val items: List<String>)

    private fun publishedCourse(required: Int = 2, optional: Int = 0): Fixture {
        val teacher = instructorTokenFor("teacher")
        val courseId = id(post("/api/v1/courses", teacher, """{"title":"Course ${System.nanoTime()}"}"""))
        val sectionId = id(post("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))

        val items = buildList {
            repeat(required) {
                add(id(post("/api/v1/sections/$sectionId/items", teacher, """{"title":"R$it","type":"LESSON"}""")))
            }
            repeat(optional) {
                add(
                    id(
                        post(
                            "/api/v1/sections/$sectionId/items",
                            teacher,
                            """{"title":"O$it","type":"LESSON","isRequired":false}""",
                        ),
                    ),
                )
            }
        }
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Fixture(teacher, courseId, items)
    }

    private fun enroll(token: String, courseId: String) =
        mockMvc.post("/api/v1/courses/$courseId/enroll") { header("Authorization", "Bearer $token") }
            .andExpect { status { isCreated() } }

    /** Gives an item a lesson with a completion rule to enforce. */
    private fun saveLesson(teacher: String, itemId: String, json: String) =
        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = json
        }.andExpect { status { isOk() } }

    private fun recordProgress(token: String, itemId: String, json: String) =
        mockMvc.post("/api/v1/items/$itemId/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }

    /**
     * The rule used to be stored and read by nothing, so an author who asked
     * for "reaching the end of the video" got a lesson a student finished by
     * opening it.
     */
    @Test
    fun `a lesson that counts as done on reaching its end refuses to finish early`() {
        val fixture = publishedCourse(required = 1)
        val itemId = fixture.items[0]
        saveLesson(
            fixture.teacher,
            itemId,
            """{"title":"Lecture","resourceType":"DOCUMENT","sourceType":"INLINE","content":"body",
                "durationSeconds":600,"completionRule":"DURATION"}""",
        )
        val student = tokenFor("student")
        enroll(student, fixture.courseId)

        recordProgress(student, itemId, """{"status":"COMPLETED","lastPositionSeconds":100}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("IN_PROGRESS") }
            }

        recordProgress(student, itemId, """{"status":"COMPLETED","lastPositionSeconds":600}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("COMPLETED") }
            }
    }

    @Test
    fun `a lesson that counts as done on being opened needs nothing else`() {
        val fixture = publishedCourse(required = 1)
        val itemId = fixture.items[0]
        saveLesson(
            fixture.teacher,
            itemId,
            """{"title":"Notice","resourceType":"DOCUMENT","sourceType":"INLINE","content":"read me",
                "completionRule":"VIEW"}""",
        )
        val student = tokenFor("student")
        enroll(student, fixture.courseId)

        recordProgress(student, itemId, """{"status":"IN_PROGRESS"}""").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("COMPLETED") }
        }
    }

    @Test
    fun `cannot enrol in an unpublished course`() {
        val teacher = instructorTokenFor("teacher")
        val student = tokenFor("student")
        val courseId = id(post("/api/v1/courses", teacher, """{"title":"Draft ${System.nanoTime()}"}"""))

        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("COURSE_NOT_PUBLISHED") }
        }
    }

    @Test
    fun `enrolling twice is a conflict`() {
        val fixture = publishedCourse()
        val student = tokenFor("student")

        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("ACTIVE") }
        }

        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("ALREADY_ENROLLED") }
        }
    }

    @Test
    fun `progress cannot be recorded without an enrolment`() {
        val fixture = publishedCourse()
        val stranger = tokenFor("stranger")

        recordProgress(stranger, fixture.items.first(), """{"status":"IN_PROGRESS"}""").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOT_ENROLLED") }
        }
    }

    @Test
    fun `progress percentage never moves backwards`() {
        val fixture = publishedCourse()
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }

        recordProgress(student, fixture.items[0], """{"status":"IN_PROGRESS","progressPercent":40.0}""")
            .andExpect { status { isOk() } }

        // A late-arriving event from earlier in the video must not undo progress.
        recordProgress(student, fixture.items[0], """{"status":"IN_PROGRESS","progressPercent":10.0}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.progressPercent") { value(40.0) }
            }
    }

    @Test
    fun `a completed item cannot be un-completed by re-watching it`() {
        val fixture = publishedCourse()
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }

        recordProgress(student, fixture.items[0], """{"status":"COMPLETED"}""")
            .andExpect { status { isOk() } }

        recordProgress(student, fixture.items[0], """{"status":"IN_PROGRESS","progressPercent":55.0}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("COMPLETED") }
                jsonPath("$.progressPercent") { value(100.0) }
            }
    }

    @Test
    fun `optional items do not count toward completion`() {
        val fixture = publishedCourse(required = 2, optional = 1)
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }

        // items[2] is the optional one.
        recordProgress(student, fixture.items[2], """{"status":"COMPLETED"}""").andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses/${fixture.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.requiredItems") { value(2) }
            jsonPath("$.completedItems") { value(0) }
            jsonPath("$.isComplete") { value(false) }
        }
    }

    @Test
    fun `completing every required item completes the enrolment`() {
        val fixture = publishedCourse(required = 2, optional = 1)
        val student = tokenFor("student")
        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }

        recordProgress(student, fixture.items[0], """{"status":"COMPLETED"}""").andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses/${fixture.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            jsonPath("$.completedItems") { value(1) }
            jsonPath("$.percentComplete") { value(50.00) }
            jsonPath("$.isComplete") { value(false) }
        }

        recordProgress(student, fixture.items[1], """{"status":"COMPLETED"}""").andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses/${fixture.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            jsonPath("$.isComplete") { value(true) }
            jsonPath("$.percentComplete") { value(100.00) }
        }

        // The enrolment closes itself once the work is done.
        mockMvc.get("/api/v1/me/enrollments") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].status") { value("COMPLETED") }
            jsonPath("$.content[0].completedAt") { exists() }
            jsonPath("$.content[0].startedAt") { exists() }
        }
    }

    @Test
    fun `a cancelled enrolment blocks further progress but keeps history`() {
        val fixture = publishedCourse()
        val student = tokenFor("student")
        val enrollment = mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val enrollmentId = objectMapper.readTree(enrollment).get("id").asString()

        recordProgress(student, fixture.items[0], """{"status":"COMPLETED"}""").andExpect { status { isOk() } }

        mockMvc.post("/api/v1/enrollments/$enrollmentId/cancel") {
            header("Authorization", "Bearer $student")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("CANCELLED") }
        }

        recordProgress(student, fixture.items[1], """{"status":"COMPLETED"}""").andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ENROLLMENT_NOT_ACTIVE") }
        }

        // Re-enrolling resumes the same record, so the earlier item stays done.
        mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/courses/${fixture.courseId}/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect { jsonPath("$.completedItems") { value(1) } }
    }

    @Test
    fun `nobody can cancel someone else's enrolment`() {
        val fixture = publishedCourse()
        val student = tokenFor("student")
        val stranger = tokenFor("stranger")
        val body = mockMvc.post("/api/v1/courses/${fixture.courseId}/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val enrollmentId = objectMapper.readTree(body).get("id").asString()

        mockMvc.post("/api/v1/enrollments/$enrollmentId/cancel") {
            header("Authorization", "Bearer $stranger")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ENROLLMENT_ACCESS_DENIED") }
        }
    }

    @Test
    fun `enrolments require authentication`() {
        mockMvc.get("/api/v1/me/enrollments").andExpect { status { isUnauthorized() } }
    }
}
