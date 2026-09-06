package com.elearning.platform.notifications

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.RecordingEmailConfiguration
import com.elearning.shared.testing.RecordingEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
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
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration

/**
 * Completion, certificates, and the notification pipeline end to end - including
 * a real RabbitMQ round trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RecordingEmailConfiguration::class)
class NotificationAndCertificateTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val emails: RecordingEmailSender,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    // Deliberately no global reset: state is per-address, and async deliveries
    // from an earlier test may still be in flight.

    private data class Account(val token: String, val email: String)

    private fun account(label: String): Account {
        val unique = "$label-${System.nanoTime()}"
        val email = "$unique@example.com"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andReturn().response.contentAsString
        return Account(objectMapper.readTree(body).get("accessToken").asString(), email)
    }

    private fun postJson(url: String, token: String, json: String): String =
        mockMvc.post(url) {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = json
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

    private fun id(json: String) = objectMapper.readTree(json).get("id").asString()
    private fun array(n: JsonNode): List<JsonNode> = (0 until n.size()).map { n.get(it) }

    private data class Course(val teacher: String, val courseId: String, val itemId: String)

    /** A published course with exactly one required lesson. */
    private fun oneLessonCourse(): Course {
        val teacher = account("teacher").token
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"L","type":"LESSON"}"""))
        mockMvc.put("/api/v1/items/$itemId/lesson") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"contentType":"ARTICLE","content":"body"}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }
        return Course(teacher, courseId, itemId)
    }

    private fun completeCourse(student: String, course: Course) {
        mockMvc.post("/api/v1/courses/${course.courseId}/enroll") { header("Authorization", "Bearer $student") }
            .andExpect { status { isCreated() } }
        mockMvc.post("/api/v1/items/${course.itemId}/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }
    }

    private fun notificationsOf(token: String): List<JsonNode> {
        val body = mockMvc.get("/api/v1/me/notifications") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        return array(objectMapper.readTree(body).get("content"))
    }

    @Test
    fun `completing a course issues a certificate and notifies the student`() {
        val course = oneLessonCourse()
        val student = account("student")
        completeCourse(student.token, course)

        val certificates = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andExpect { status { isOk() } }.andReturn().response.contentAsString,
            ),
        )
        assertThat(certificates).hasSize(1)
        assertThat(certificates[0].get("certificateNumber").asString()).startsWith("ELP-")
        assertThat(certificates[0].get("revokedAt")).isNull()

        // Notifications are published after commit and delivered off a queue.
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val types = notificationsOf(student.token).map { it.get("type").asString() }
            assertThat(types).contains("COURSE_COMPLETED", "CERTIFICATE_ISSUED")
        }

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(emails.sentTo(student.email)).isNotEmpty()
        }
    }

    @Test
    fun `a certificate is issued once, however often completion is reached`() {
        val course = oneLessonCourse()
        val student = account("student")
        completeCourse(student.token, course)

        // Re-recording completion must not mint a second certificate.
        mockMvc.post("/api/v1/items/${course.itemId}/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${student.token}")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }

        val certificates = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andReturn().response.contentAsString,
            ),
        )
        assertThat(certificates).hasSize(1)
    }

    @Test
    fun `anyone holding the code can verify a certificate without an account`() {
        val course = oneLessonCourse()
        val student = account("student")
        completeCourse(student.token, course)

        val certificate = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andReturn().response.contentAsString,
            ),
        ).first()
        val code = certificate.get("verificationCode").asString()

        // No Authorization header.
        mockMvc.get("/api/v1/certificates/verify/$code").andExpect {
            status { isOk() }
            jsonPath("$.valid") { value(true) }
            jsonPath("$.certificateNumber") { value(certificate.get("certificateNumber").asString()) }
        }
    }

    @Test
    fun `the certificate PDF is rendered after commit and downloadable by its holder`() {
        val course = oneLessonCourse()
        val student = account("student")
        completeCourse(student.token, course)

        val certificate = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andReturn().response.contentAsString,
            ),
        ).first()
        val certificateId = certificate.get("id").asString()

        // Rendering happens after the completion transaction commits.
        val url = await().atMost(Duration.ofSeconds(30)).until({
            val response = mockMvc.get("/api/v1/certificates/$certificateId/download-url") {
                header("Authorization", "Bearer ${student.token}")
            }.andReturn().response
            if (response.status == 200) {
                objectMapper.readTree(response.contentAsString).get("downloadUrl").asString()
            } else {
                null
            }
        }, { it != null })!!

        val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
        val bytes = connection.inputStream.readBytes()
        connection.disconnect()

        // A real PDF, not an empty or placeholder object.
        assertThat(String(bytes.copyOfRange(0, 5))).isEqualTo("%PDF-")
        assertThat(bytes.size).isGreaterThan(500)
    }

    @Test
    fun `somebody else cannot download your certificate`() {
        val course = oneLessonCourse()
        val student = account("student")
        val stranger = account("stranger")
        completeCourse(student.token, course)

        val certificateId = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andReturn().response.contentAsString,
            ),
        ).first().get("id").asString()

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            mockMvc.get("/api/v1/certificates/$certificateId/download-url") {
                header("Authorization", "Bearer ${student.token}")
            }.andExpect { status { isOk() } }
        }

        mockMvc.get("/api/v1/certificates/$certificateId/download-url") {
            header("Authorization", "Bearer ${stranger.token}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("CERTIFICATE_ACCESS_DENIED") }
        }
    }

    @Test
    fun `an unknown verification code is not found`() {
        mockMvc.get("/api/v1/certificates/verify/definitely-not-a-real-code").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CERTIFICATE_NOT_FOUND") }
        }
    }

    @Test
    fun `course staff can revoke a certificate and verification then reports it`() {
        val course = oneLessonCourse()
        val student = account("student")
        completeCourse(student.token, course)

        val certificate = array(
            objectMapper.readTree(
                mockMvc.get("/api/v1/me/certificates") { header("Authorization", "Bearer ${student.token}") }
                    .andReturn().response.contentAsString,
            ),
        ).first()

        // The holder cannot revoke their own certificate.
        mockMvc.post("/api/v1/certificates/${certificate.get("id").asString()}/revoke") {
            header("Authorization", "Bearer ${student.token}")
        }.andExpect { status { isForbidden() } }

        mockMvc.post("/api/v1/certificates/${certificate.get("id").asString()}/revoke") {
            header("Authorization", "Bearer ${course.teacher}")
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/certificates/verify/${certificate.get("verificationCode").asString()}")
            .andExpect {
                status { isOk() }
                jsonPath("$.valid") { value(false) }
                jsonPath("$.revokedAt") { exists() }
            }
    }

    @Test
    fun `grading an assignment notifies the student`() {
        val teacher = account("teacher").token
        val courseId = id(postJson("/api/v1/courses", teacher, """{"title":"C ${System.nanoTime()}"}"""))
        val sectionId = id(postJson("/api/v1/courses/$courseId/sections", teacher, """{"title":"S"}"""))
        val itemId = id(postJson("/api/v1/sections/$sectionId/items", teacher, """{"title":"A","type":"ASSIGNMENT"}"""))
        mockMvc.put("/api/v1/items/$itemId/assignment") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"maxScore":100}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $teacher") }
            .andExpect { status { isOk() } }

        val student = account("student")
        mockMvc.post("/api/v1/courses/$courseId/enroll") { header("Authorization", "Bearer ${student.token}") }
            .andExpect { status { isCreated() } }
        val submissionId = id(
            postJson("/api/v1/items/$itemId/assignment/submissions", student.token, """{"content":"done"}"""),
        )

        mockMvc.post("/api/v1/submissions/$submissionId/grade") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"score":90,"feedback":"good"}"""
        }.andExpect { status { isOk() } }

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            val types = notificationsOf(student.token).map { it.get("type").asString() }
            assertThat(types).contains("ASSIGNMENT_GRADED")
        }
    }

    @Test
    fun `a failing mail server does not lose the in-app notification`() {
        val course = oneLessonCourse()
        val student = account("student")
        // Scoped to this student, so async deliveries from other tests are safe.
        emails.failFor(student.email)
        completeCourse(student.token, course)

        // The business operation succeeded and the notification is still there.
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(notificationsOf(student.token)).isNotEmpty()
        }
        assertThat(emails.sentTo(student.email)).isEmpty()
    }

    @Test
    fun `notifications are private, readable, and counted`() {
        val course = oneLessonCourse()
        val student = account("student")
        val stranger = account("stranger")
        completeCourse(student.token, course)

        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            assertThat(notificationsOf(student.token)).isNotEmpty()
        }

        mockMvc.get("/api/v1/me/notifications/unread-count") {
            header("Authorization", "Bearer ${student.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.unread") { value(2) }
        }

        val first = notificationsOf(student.token).first().get("id").asString()

        // Somebody else's notification is not readable.
        mockMvc.post("/api/v1/notifications/$first/read") {
            header("Authorization", "Bearer ${stranger.token}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("NOTIFICATION_ACCESS_DENIED") }
        }

        mockMvc.post("/api/v1/notifications/$first/read") {
            header("Authorization", "Bearer ${student.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.readAt") { exists() }
        }

        mockMvc.post("/api/v1/me/notifications/read-all") {
            header("Authorization", "Bearer ${student.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.unread") { value(0) }
        }
    }

    @Test
    fun `notifications require authentication`() {
        mockMvc.get("/api/v1/me/notifications").andExpect { status { isUnauthorized() } }
    }
}
