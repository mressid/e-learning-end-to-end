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
import tools.jackson.databind.ObjectMapper

/**
 * The dashboard's directories, and the permissions that gate them.
 *
 * A directory has no relationship to derive authority from - there is no edge
 * between an administrator and "all users" - so these are the endpoints where a
 * permission is the only honest check (§11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDirectoryApiTest(
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

    /** An admin holding exactly the permissions named, and nothing else. */
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

    private fun learner(label: String): Pair<String, String> {
        val unique = "$label-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val id = objectMapper.readTree(body).get("id").asString()
        val token = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
        return id to token
    }

    // ---- the learner directory -------------------------------------------

    @Test
    fun `a super admin can list learners`() {
        learner("listed")
        mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer ${superToken()}")
            param("size", "5")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { exists() }
            jsonPath("$.totalElements") { exists() }
        }
    }

    @Test
    fun `the directory is searchable by email`() {
        val unique = "findme-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }

        val body = mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer ${superToken()}")
            param("q", unique)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        assertThat(objectMapper.readTree(body).get("totalElements").asInt()).isEqualTo(1)
    }

    @Test
    fun `reading the directory needs user_read, not merely an admin account`() {
        // Holding an admin account is not authority; the permission is.
        val token = adminWith("audit.read")
        mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `a learner's own token cannot read the directory`() {
        val (_, token) = learner("nosy")
        mockMvc.get("/api/v1/admin/users") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `suspending needs its own permission and ends the learner's sessions`() {
        val (userId, _) = learner("suspendme")
        val readOnly = adminWith("user.read")

        // Reading the directory does not imply changing what is in it.
        mockMvc.post("/api/v1/admin/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $readOnly")
            content = """{"status":"SUSPENDED"}"""
        }.andExpect { status { isForbidden() } }

        val both = adminWith("user.read", "user.suspend")
        mockMvc.post("/api/v1/admin/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $both")
            content = """{"status":"SUSPENDED"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("SUSPENDED") }
        }
    }

    @Test
    fun `a suspended learner cannot sign in again`() {
        val unique = "locked-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val userId = objectMapper.readTree(body).get("id").asString()

        mockMvc.post("/api/v1/admin/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = """{"status":"SUSPENDED"}"""
        }.andExpect { status { isOk() } }

        // Suspension is indistinguishable from bad credentials: unlike an
        // unverified address, it is not the caller's state to learn.
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `an administrator cannot push an account back to PENDING`() {
        val (userId, _) = learner("pendingback")
        // PENDING means "has not confirmed their address", a state only the
        // registration flow produces. Setting it by hand would fake that.
        mockMvc.post("/api/v1/admin/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = """{"status":"PENDING"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_STATUS") }
        }
    }

    // ---- the instructor roster -------------------------------------------

    @Test
    fun `authoring a course puts a learner on the roster`() {
        val (_, teacher) = learner("teacher")
        repeat(2) {
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Owned ${System.nanoTime()}"}"""
            }.andExpect { status { isCreated() } }
        }

        val body = mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer ${superToken()}")
            param("size", "100")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val content = objectMapper.readTree(body).get("content")
        val counts = (0 until content.size()).map { content.get(it).get("courseCount").asInt() }
        assertThat(counts).isNotEmpty()
        // Since V11 the roster reads `users.is_instructor` rather than deriving
        // itself from `courses.owner_id`, so creating a course sets the flag.
        // Without that the two would disagree: creation is still open to any
        // learner, and a self-service author would own courses while being
        // absent from the list of people who author courses.
        //
        // The counts are therefore no longer all positive - an instructor an
        // administrator added has not started anything yet. That absence is the
        // point of the change, so it is asserted in AdminInstructorApiTest
        // rather than excluded here.
        assertThat(counts).anyMatch { it > 0 }
    }

    @Test
    fun `the roster needs user_read too`() {
        mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect { status { isForbidden() } }
    }
}
