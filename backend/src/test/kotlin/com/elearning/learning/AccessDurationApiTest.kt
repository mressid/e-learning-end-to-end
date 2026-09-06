package com.elearning.learning

import com.elearning.learning.domain.EnrollmentStatus
import com.elearning.learning.infrastructure.EnrollmentRepository
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
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Time-limited access, set per course.
 *
 * `enrollments.expires_at` existed from V1 and nothing ever wrote it, so the
 * lockout and the sweeper were both inert. These tests are about the policy
 * that fills the gap - and about the trap it opens if re-enrolment is careless.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessDurationApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val enrollments: EnrollmentRepository,
    @Autowired val sweeper: com.elearning.learning.application.EnrollmentExpirySweeper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun tokenFor(label: String): Pair<String, UUID> {
        val unique = "$label-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = UUID.fromString(objectMapper.readTree(body).get("id").asString())
        val token = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
        return token to id
    }

    /** A published course, optionally granting a limited window. */
    private fun course(owner: String, days: Int?): Pair<String, String> {
        val payload = mutableMapOf<String, Any>("title" to "Timed ${System.nanoTime()}")
        if (days != null) payload["accessDurationDays"] = days
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = objectMapper.writeValueAsString(payload)
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val sectionId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"Section"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val itemId = objectMapper.readTree(
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"Lesson","type":"LESSON"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }
        return courseId to itemId
    }

    @Test
    fun `a course with no duration grants access that never lapses`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, _) = course(owner, days = null)
        val (student, studentId) = tokenFor("student")

        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        // Null is the default and what every enrolment had before this existed.
        val enrolment = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId)).orElseThrow()
        assertThat(enrolment.expiresAt).isNull()
    }

    @Test
    fun `enrolling in a timed course stamps the window onto the enrolment`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, _) = course(owner, days = 30)
        val (student, studentId) = tokenFor("student")

        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        val enrolment = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId)).orElseThrow()
        val expected = Instant.now().plus(30, ChronoUnit.DAYS)
        assertThat(enrolment.expiresAt).isNotNull()
        assertThat(enrolment.expiresAt).isBetween(expected.minusSeconds(120), expected.plusSeconds(120))
    }

    @Test
    fun `shortening a course's window never shortens access already granted`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, _) = course(owner, days = 365)
        val (student, studentId) = tokenFor("student")
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
        val granted = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId))
            .orElseThrow().expiresAt

        mockMvc.patch("/api/v1/courses/$courseId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"accessDurationDays":1}"""
        }.andExpect { status { isOk() } }

        // The window is copied at enrolment, not read live: the deal somebody
        // already has is not the author's to revise.
        val after = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId))
            .orElseThrow().expiresAt
        assertThat(after).isEqualTo(granted)
    }

    @Test
    fun `an expired enrolment refuses work and the sweep makes its status honest`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, itemId) = course(owner, days = 30)
        val (student, studentId) = tokenFor("student")
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        val enrolment = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId)).orElseThrow()
        enrolment.expiresAt = Instant.now().minusSeconds(60)
        enrollments.saveAndFlush(enrolment)

        // isClosed() is what actually refuses, before any sweep runs.
        mockMvc.post("/api/v1/items/$itemId/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("ENROLLMENT_NOT_ACTIVE") }
        }

        // The sweeper's job is the stored status, so listings stop saying ACTIVE.
        assertThat(sweeper.sweep()).isGreaterThan(0)
        assertThat(
            enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId)).orElseThrow().status,
        ).isEqualTo(EnrollmentStatus.EXPIRED)
    }

    @Test
    fun `re-enrolling after expiry starts a fresh window instead of returning a dead one`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, itemId) = course(owner, days = 30)
        val (student, studentId) = tokenFor("student")
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        val enrolment = enrollments.findByStudentIdAndCourseId(studentId, UUID.fromString(courseId)).orElseThrow()
        enrolment.expiresAt = Instant.now().minusSeconds(60)
        enrollments.saveAndFlush(enrolment)
        sweeper.sweep()

        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        // Without a new window this would come back ACTIVE and refuse every
        // request: isClosed() reads expiresAt whatever the status says.
        mockMvc.post("/api/v1/items/$itemId/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `the duration is visible on the course so an author can see what they set`() {
        val (owner, _) = tokenFor("owner")
        val (courseId, _) = course(owner, days = 90)
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessDurationDays") { value(90) }
        }
    }

    @Test
    fun `a zero or negative duration is rejected rather than stored`() {
        val (owner, _) = tokenFor("owner")
        mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Bad ${System.nanoTime()}","accessDurationDays":0}"""
        }.andExpect { status { isBadRequest() } }
    }
}
