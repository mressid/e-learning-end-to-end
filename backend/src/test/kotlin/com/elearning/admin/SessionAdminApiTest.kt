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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * The session manager - the part of the dashboard's Security section that has
 * real data behind it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionAdminApiTest(
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

    /** Registers a learner and signs in, returning (userId, refreshToken). */
    private fun signedInLearner(label: String): Pair<String, String> {
        val unique = "$label-${System.nanoTime()}"
        val userId = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val refresh = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()
        return userId to refresh
    }

    private fun sessionsOf(token: String, userId: String) = objectMapper.readTree(
        mockMvc.get("/api/v1/admin/sessions") {
            header("Authorization", "Bearer $token")
            param("userId", userId)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("content")

    @Test
    fun `a login shows up as exactly one session`() {
        val su = superToken()
        val (userId, _) = signedInLearner("oneup")

        val sessions = sessionsOf(su, userId)
        assertThat(sessions.size()).isEqualTo(1)
        val session = sessions.get(0)
        assertThat(session.get("subjectId").asString()).isEqualTo(userId)
        assertThat(session.get("subjectLabel").asString()).isNotBlank()
        assertThat(session.get("refreshCount").asInt()).isZero()
    }

    @Test
    fun `refreshing does not create a second session`() {
        val su = superToken()
        val (userId, refresh) = signedInLearner("rotating")

        val next = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("refreshToken" to refresh))
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to next))
        }.andExpect { status { isOk() } }

        // Rotation mints a successor each time; counting tokens would report
        // one login as three sessions.
        val sessions = sessionsOf(su, userId)
        assertThat(sessions.size()).isEqualTo(1)
        assertThat(sessions.get(0).get("refreshCount").asInt()).isEqualTo(2)
    }

    @Test
    fun `two devices are two sessions, and ending one leaves the other`() {
        val su = superToken()
        val unique = "twodevices-${System.nanoTime()}"
        val userId = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        fun login() = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        login()
        val laptop = login()
        assertThat(sessionsOf(su, userId).size()).isEqualTo(2)

        val phoneSession = sessionsOf(su, userId).let { list ->
            (0 until list.size()).map { list.get(it) }
        }.last().get("sessionId").asString()

        mockMvc.delete("/api/v1/admin/sessions/$phoneSession") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }

        assertThat(sessionsOf(su, userId).size()).isEqualTo(1)
        // The untouched device keeps working.
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to laptop))
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `ending a session kills the whole chain, not just its newest token`() {
        val su = superToken()
        val (userId, refresh) = signedInLearner("chain")
        val newer = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/refresh") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(mapOf("refreshToken" to refresh))
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        val sessionId = sessionsOf(su, userId).get(0).get("sessionId").asString()
        mockMvc.delete("/api/v1/admin/sessions/$sessionId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }

        // Revoking only the newest would leave predecessors able to rotate a
        // fresh chain back into existence.
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to newer))
        }.andExpect { status { isUnauthorized() } }
        assertThat(sessionsOf(su, userId).size()).isZero()
    }

    @Test
    fun `signing a learner out everywhere ends every device`() {
        val su = superToken()
        val unique = "everywhere-${System.nanoTime()}"
        val userId = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        repeat(3) {
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andExpect { status { isOk() } }
        }
        assertThat(sessionsOf(su, userId).size()).isEqualTo(3)

        mockMvc.delete("/api/v1/admin/sessions/users/$userId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }
        assertThat(sessionsOf(su, userId).size()).isZero()
    }

    @Test
    fun `administrator sessions are listed separately`() {
        val su = superToken()
        mockMvc.get("/api/v1/admin/sessions/admins") {
            header("Authorization", "Bearer $su")
        }.andExpect {
            // Administrators are a separate table, so a separate chain.
            status { isOk() }
            jsonPath("$.totalElements") { exists() }
        }
    }

    @Test
    fun `the session manager needs settings_manage`() {
        mockMvc.get("/api/v1/admin/sessions") {
            header("Authorization", "Bearer ${adminWith("user.read")}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `ending a session is recorded in the audit trail`() {
        val su = superToken()
        val (userId, _) = signedInLearner("audited")
        val sessionId = sessionsOf(su, userId).get(0).get("sessionId").asString()

        mockMvc.delete("/api/v1/admin/sessions/$sessionId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }

        val entries = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/audit") {
                header("Authorization", "Bearer $su")
                param("action", "session.revoked")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("content")
        assertThat(entries.size()).isGreaterThan(0)
    }

    @Test
    fun `ending a session that does not exist is a 404`() {
        mockMvc.delete("/api/v1/admin/sessions/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect { status { isNotFound() } }
    }
}
