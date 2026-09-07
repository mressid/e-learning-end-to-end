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
 * The audit trail: that privilege changes are recorded, attributed, and cannot
 * be edited away afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuditApiTest(
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

    private fun audit(token: String, action: String? = null) = objectMapper.readTree(
        mockMvc.get("/api/v1/admin/audit") {
            header("Authorization", "Bearer $token")
            if (action != null) param("action", action)
            param("size", "50")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    @Test
    fun `granting a role is recorded, attributed and described`() {
        val su = superToken()
        // adminWith performs a create + role creation + grant.
        adminWith("audit.read")

        val entries = audit(su, "role.assigned").get("content")
        assertThat(entries.size()).isGreaterThan(0)
        val entry = entries.get(0)

        assertThat(entry.get("actorType").asString()).isEqualTo("ADMIN")
        // Snapshotted at the time, so a later rename cannot rewrite the past.
        assertThat(entry.get("actorLabel").asString()).isEqualTo("superadmin")
        assertThat(entry.get("summary").asString()).contains("Granted role")
        assertThat(entry.get("targetType").asString()).isEqualTo("ADMIN_USER")
        // Ties the entry to its request and to the application logs.
        assertThat(entry.get("requestId").asString()).isNotBlank()
    }

    @Test
    fun `role creation and deletion are both recorded`() {
        val su = superToken()
        val name = "Ephemeral ${System.nanoTime()}"
        val roleId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/roles") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("name" to name, "permissions" to listOf("audit.read")),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        mockMvc.delete("/api/v1/admin/roles/$roleId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }

        val forRole = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/audit") {
                header("Authorization", "Bearer $su")
                param("targetType", "ROLE")
                param("targetId", roleId)
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("content")

        val actions = (0 until forRole.size()).map { forRole.get(it).get("action").asString() }
        assertThat(actions).contains("role.created", "role.deleted")
        // The record survives the role it describes - that is the point of
        // having no foreign key on the target.
        mockMvc.get("/api/v1/admin/roles") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `suspending a learner is recorded with the transition`() {
        val su = superToken()
        val unique = "audited-${System.nanoTime()}"
        val userId = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/register") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        mockMvc.post("/api/v1/admin/users/$userId/status") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"status":"SUSPENDED"}"""
        }.andExpect { status { isOk() } }

        val entries = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/audit") {
                header("Authorization", "Bearer $su")
                param("targetType", "USER")
                param("targetId", userId)
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("content")

        val entry = entries.get(0)
        assertThat(entry.get("action").asString()).isEqualTo("user.status_changed")
        // Both ends of the transition, so the entry stands on its own without
        // reconstructing prior state from other rows.
        assertThat(entry.get("details").get("from").asString()).isEqualTo("ACTIVE")
        assertThat(entry.get("details").get("to").asString()).isEqualTo("SUSPENDED")
    }

    @Test
    fun `the trail can be followed by actor`() {
        val su = superToken()
        val me = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/auth/me") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        ).get("id").asString()

        adminWith("audit.read")

        val mine = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/audit") {
                header("Authorization", "Bearer $su")
                param("actorId", me)
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("content")
        assertThat(mine.size()).isGreaterThan(0)
        assertThat((0 until mine.size()).map { mine.get(it).get("actorId").asString() })
            .allMatch { it == me }
    }

    @Test
    fun `reading the trail needs audit_read`() {
        mockMvc.get("/api/v1/admin/audit") {
            header("Authorization", "Bearer ${adminWith("user.read")}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `summaries interpolate their values instead of recording the template`() {
        // Regression. Every summary in the codebase was written with Kotlin's
        // ${'$'} escape, which produces a *literal* dollar sign - so the trail
        // stored `Created role "$name"` rather than the name. It compiled, the
        // endpoint returned 200, and existing assertions like
        // `contains("Created role")` passed, because that half of the string was
        // never the broken half. Only reading a stored row showed it.
        val su = superToken()
        val name = "Interpolated ${System.nanoTime()}"
        mockMvc.post("/api/v1/admin/roles") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(
                mapOf("name" to name, "permissions" to listOf("audit.read")),
            )
        }.andExpect { status { isCreated() } }

        val summary = audit(su, "role.created").get("content").get(0).get("summary").asString()
        assertThat(summary).contains(name)
        assertThat(summary).doesNotContain("\${")
    }

    @Test
    fun `the trail cannot be written to or deleted from`() {
        val su = superToken()
        // A log with a delete button is not evidence of anything, so there is
        // deliberately no write surface at all.
        mockMvc.post("/api/v1/admin/audit") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"action":"invented","summary":"never happened"}"""
        }.andExpect { status { isMethodNotAllowed() } }

        mockMvc.delete("/api/v1/admin/audit") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isMethodNotAllowed() } }
    }
}
