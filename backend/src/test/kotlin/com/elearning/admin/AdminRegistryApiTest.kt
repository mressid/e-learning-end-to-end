package com.elearning.admin

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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/**
 * The certificate register and the grading queue: the dashboard's two
 * cross-course read models.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRegistryApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun superToken(): String = objectMapper.readTree(
        mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@elearning.local","password":"change this password now"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("accessToken").asString()

    private fun adminWith(vararg permissions: String): String {
        val su = superToken()
        val unique = "scoped-${System.nanoTime()}"
        val roleId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/roles") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "Role $unique", "permissions" to permissions.toList()),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val adminId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/admins") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("email" to "$unique@example.com", "username" to unique, "password" to password),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        mockMvc.post("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }
        return objectMapper.readTree(
            mockMvc.post("/api/v1/admin/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("email" to "$unique@example.com", "password" to password),
                )
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    private fun learner(label: String): String {
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

    /** A published course whose single required item the student completes. */
    private fun completedCourse(teacher: String, student: String): String {
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Certified ${System.nanoTime()}"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val sectionId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Section"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val itemId = objectMapper.readTree(
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Lesson","type":"LESSON"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $teacher")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
        mockMvc.post("/api/v1/items/$itemId/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }
        return courseId
    }

    // ---- certificates ----------------------------------------------------

    @Test
    fun `the register lists issued certificates with holder and course resolved`() {
        val teacher = learner("teacher")
        val student = learner("student")
        val courseId = completedCourse(teacher, student)

        val body = mockMvc.get("/api/v1/admin/certificates") {
            header("Authorization", "Bearer ${superToken()}")
            param("courseId", courseId)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val row = objectMapper.readTree(body).get("content").get(0)
        assertThat(row.get("certificateNumber").asString()).isNotBlank()
        // Resolved for the page, so the dashboard shows people not UUIDs.
        assertThat(row.get("studentEmail").asString()).contains("@")
        assertThat(row.get("courseTitle").asString()).isNotBlank()
        assertThat(row.get("valid").asBoolean()).isTrue()
    }

    @Test
    fun `the verification code is never listed`() {
        val teacher = learner("teacher")
        val student = learner("student")
        completedCourse(teacher, student)

        val body = mockMvc.get("/api/v1/admin/certificates") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        // It is the unguessable half of the public check, so listing it for
        // staff would turn a screenshot into forgeable credentials. Asserting
        // the field is absent, not merely null.
        assertThat(body).doesNotContain("verificationCode")
    }

    @Test
    fun `the register needs certificate_read`() {
        mockMvc.get("/api/v1/admin/certificates") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `revoked certificates can be filtered out`() {
        mockMvc.get("/api/v1/admin/certificates") {
            header("Authorization", "Bearer ${superToken()}")
            param("revoked", "false")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content[0].valid") { value(true) }
        }
    }

    // ---- the grading queue -----------------------------------------------

    @Test
    fun `the queue lists submissions with their author and assignment named`() {
        val teacher = learner("teacher")
        val student = learner("student")
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Assigned ${System.nanoTime()}"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val sectionId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Section"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val itemId = objectMapper.readTree(
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Final Essay","type":"ASSIGNMENT"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        mockMvc.put("/api/v1/items/$itemId/assignment") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"instructions":"Write something","maxScore":100}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $teacher")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
        mockMvc.post("/api/v1/items/$itemId/assignment/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"content":"my answer"}"""
        }.andExpect { status { isCreated() } }

        val body = mockMvc.get("/api/v1/admin/submissions") {
            header("Authorization", "Bearer ${superToken()}")
            param("status", "SUBMITTED")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val row = objectMapper.readTree(body).get("content").get(0)
        assertThat(row.get("assignmentTitle").asString()).isEqualTo("Final Essay")
        assertThat(row.get("courseTitle").asString()).isNotBlank()
        assertThat(row.get("studentEmail").asString()).contains("@")
        // The queue says what is outstanding and whose; the answers themselves
        // are for whoever marks them.
        assertThat(body).doesNotContain("my answer")
    }

    @Test
    fun `the queue needs submission_read`() {
        mockMvc.get("/api/v1/admin/submissions") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an unknown submission status is rejected`() {
        mockMvc.get("/api/v1/admin/submissions") {
            header("Authorization", "Bearer ${superToken()}")
            param("status", "NONSENSE")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_STATUS") }
        }
    }

    @Test
    fun `there is no way to grade from the dashboard`() {
        // graded_by references `users`, and an administrator is not one - there
        // is nowhere to record them as the grader, so marking stays with the
        // course's instructors rather than being faked here.
        mockMvc.post("/api/v1/admin/submissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = """{"score":90}"""
        }.andExpect { status { isMethodNotAllowed() } }
    }
}
