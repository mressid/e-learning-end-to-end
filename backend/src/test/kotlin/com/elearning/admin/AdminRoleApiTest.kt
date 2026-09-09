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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * Platform administration: a seeded super admin, roles they assemble, and the
 * escalations the scheme has to refuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminRoleApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val seededEmail = "admin@elearning.local"
    private val seededPassword = "change this password now"
    private val strongPassword = "correct horse battery staple"

    private fun loginAdmin(email: String = seededEmail, password: String = seededPassword) =
        mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        }

    private fun superToken(): String = objectMapper.readTree(
        loginAdmin().andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("accessToken").asString()

    private fun createAdmin(token: String, label: String): String {
        val unique = "$label-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/admin/admins") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf("email" to "$unique@example.com", "username" to unique, "password" to strongPassword),
            )
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    /** Jackson 3's node types do not map cleanly through Kotlin's `map`. */
    private fun strings(node: tools.jackson.databind.JsonNode): List<String> =
        (0 until node.size()).map { node.get(it).asString() }

    private fun createRole(token: String, name: String, permissions: List<String>): String {
        val body = mockMvc.post("/api/v1/admin/roles") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf("name" to "$name ${System.nanoTime()}", "permissions" to permissions),
            )
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    // ---- the seeded bootstrap -------------------------------------------

    @Test
    fun `the migration seeds a super admin who can sign in`() {
        val body = loginAdmin().andExpect { status { isOk() } }.andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        assertThat(json.get("refreshToken").asString()).isNotBlank()

        // A super admin holds the whole catalogue, including any permission a
        // later migration adds - that is what the is_super flag means.
        val permissions = strings(json.get("permissions"))
        assertThat(permissions).contains("review.moderate", "certificate.revoke", "audit.read")
    }

    @Test
    fun `the permission catalogue is seeded reference data with no way to add to it`() {
        val token = superToken()
        mockMvc.get("/api/v1/admin/permissions") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(17) }
        }
        // No create endpoint: an invented code would name an authority that
        // nothing in the application actually checks.
        mockMvc.post("/api/v1/admin/permissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"code":"invented","description":"nope"}"""
        }.andExpect { status { isMethodNotAllowed() } }
    }

    // ---- token separation ------------------------------------------------

    @Test
    fun `an admin token is not a learner identity`() {
        val token = superToken()
        // Administrators live in their own table, so this id matches no `users`
        // row. Without the typ claim it would be accepted here and fail deeper
        // in confusing ways.
        mockMvc.get("/api/v1/me") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a learner token cannot reach the dashboard`() {
        val unique = "learner-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$strongPassword"}"""
        }.andExpect { status { isCreated() } }
        val learner = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$strongPassword"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()

        mockMvc.get("/api/v1/admin/roles") {
            header("Authorization", "Bearer $learner")
        }.andExpect { status { isForbidden() } }
    }

    // ---- roles -----------------------------------------------------------

    @Test
    fun `a super admin assembles a role and assigns it`() {
        val token = superToken()
        val roleId = createRole(token, "Moderator", listOf("review.moderate", "discussion.moderate"))
        val adminId = createAdmin(token, "mod")

        mockMvc.post("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        mockMvc.get("/api/v1/admin/admins/$adminId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.roles.length()") { value(1) }
        }
    }

    @Test
    fun `a role's permissions reach the holder's token`() {
        val token = superToken()
        val roleId = createRole(token, "Reviewer", listOf("review.moderate"))
        val adminId = createAdmin(token, "reviewer")
        mockMvc.post("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$adminId") {
                header("Authorization", "Bearer $token")
            }.andReturn().response.contentAsString,
        ).get("email").asString()

        val session = objectMapper.readTree(
            loginAdmin(email, strongPassword).andExpect { status { isOk() } }
                .andReturn().response.contentAsString,
        )
        assertThat(strings(session.get("permissions"))).isEqualTo(listOf("review.moderate"))
    }

    @Test
    fun `an ordinary admin cannot manage roles`() {
        val token = superToken()
        val adminId = createAdmin(token, "plain")
        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$adminId") {
                header("Authorization", "Bearer $token")
            }.andReturn().response.contentAsString,
        ).get("email").asString()
        val plain = objectMapper.readTree(
            loginAdmin(email, strongPassword).andReturn().response.contentAsString,
        ).get("accessToken").asString()

        // Managing roles is the super flag, never a grantable permission: if it
        // were grantable, a role could confer the power to become a super admin.
        mockMvc.get("/api/v1/admin/roles") {
            header("Authorization", "Bearer $plain")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("SUPER_ADMIN_ONLY") }
        }
    }

    @Test
    fun `an unknown permission code is refused`() {
        val token = superToken()
        mockMvc.post("/api/v1/admin/roles") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"name":"Bogus ${System.nanoTime()}","permissions":["not.a.real.permission"]}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("PERMISSION_NOT_FOUND") }
        }
    }

    @Test
    fun `the super admin role cannot be edited or deleted`() {
        val token = superToken()
        val roles = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/roles") {
                header("Authorization", "Bearer $token")
            }.andReturn().response.contentAsString,
        )
        val superRole = roles.first { it.get("isSuper").asBoolean() }
        val id = superRole.get("id").asString()

        // Writing a fixed list onto it would turn "every permission, including
        // future ones" into "every permission as of today".
        mockMvc.put("/api/v1/admin/roles/$id/permissions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"permissions":["audit.read"]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("SUPER_ROLE_IMMUTABLE") }
        }

        mockMvc.delete("/api/v1/admin/roles/$id") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("SYSTEM_ROLE") }
        }
    }

    @Test
    fun `the last super admin cannot be demoted`() {
        val token = superToken()
        val me = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/auth/me") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )
        val myId = me.get("id").asString()
        val superRoleId = me.get("roles").first { it.get("isSuper").asBoolean() }.get("id").asString()

        // Otherwise the platform locks itself out: nobody is left who can grant
        // the role back, and the only repair is SQL.
        mockMvc.delete("/api/v1/admin/admins/$myId/roles/$superRoleId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("LAST_SUPER_ADMIN") }
        }
    }

    @Test
    fun `changing a role ends the holder's sessions`() {
        val token = superToken()
        val roleId = createRole(token, "Temp", listOf("audit.read"))
        val adminId = createAdmin(token, "temp")
        mockMvc.post("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$adminId") {
                header("Authorization", "Bearer $token")
            }.andReturn().response.contentAsString,
        ).get("email").asString()
        val refresh = objectMapper.readTree(
            loginAdmin(email, strongPassword).andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        mockMvc.delete("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        // A signed token cannot be withdrawn, so the session is ended instead:
        // the stale permissions expire with the access token rather than
        // lasting the refresh token's thirty days.
        mockMvc.post("/api/v1/admin/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refresh))
        }.andExpect { status { isUnauthorized() } }
    }
    // ---- resetting a lost password ---------------------------------------

    @Test
    fun `a super admin sets a lost password and the owner signs in with it`() {
        val su = superToken()
        val adminId = createAdmin(su, "forgetful")
        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$adminId") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        ).get("email").asString()

        val replacement = "a whole new passphrase entirely"
        mockMvc.post("/api/v1/admin/admins/$adminId/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to replacement))
        }.andExpect { status { isNoContent() } }

        loginAdmin(email, replacement).andExpect { status { isOk() } }
        // The point of a reset is that the old one stops working.
        loginAdmin(email, strongPassword).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `resetting a password ends the sessions that password opened`() {
        val su = superToken()
        val adminId = createAdmin(su, "compromised")
        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$adminId") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        ).get("email").asString()
        val refresh = objectMapper.readTree(
            loginAdmin(email, strongPassword).andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        mockMvc.post("/api/v1/admin/admins/$adminId/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "another long passphrase"))
        }.andExpect { status { isNoContent() } }

        // A forgotten password is often a shared one. Replacing it would mean
        // little if whoever already had it kept their session.
        mockMvc.post("/api/v1/admin/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refresh))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a super admin cannot reset their own password this way`() {
        // /admin/auth/me/password asks for the current password precisely so a
        // borrowed session cannot lock the owner out. This must not be a way
        // around that.
        val su = superToken()
        val meId = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/auth/me") {
                header("Authorization", "Bearer $su")
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        mockMvc.post("/api/v1/admin/admins/$meId/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "trying to skip the check"))
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("CANNOT_RESET_OWN_PASSWORD") }
        }

        // And the account is untouched.
        loginAdmin().andExpect { status { isOk() } }
    }

    @Test
    fun `an ordinary admin cannot reset anybody's password`() {
        val su = superToken()
        val victimId = createAdmin(su, "victim")
        val roleId = createRole(su, "Support", listOf("user.read", "user.write"))
        val bystanderId = createAdmin(su, "bystander")
        mockMvc.post("/api/v1/admin/admins/$bystanderId/roles/$roleId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }

        val email = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/admins/$bystanderId") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        ).get("email").asString()
        val theirToken = objectMapper.readTree(
            loginAdmin(email, strongPassword).andReturn().response.contentAsString,
        ).get("accessToken").asString()

        // Holding `user.write` reaches into the learner directory, not into the
        // dashboard's own accounts.
        mockMvc.post("/api/v1/admin/admins/$victimId/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $theirToken")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "let me in please now"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("SUPER_ADMIN_ONLY") }
        }
    }

    @Test
    fun `an administrator can be created holding roles already`() {
        val su = superToken()
        val unique = "withroles-${System.nanoTime()}"
        val roleId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/roles") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "Role $unique", "permissions" to listOf("user.read")),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        val body = mockMvc.post("/api/v1/admin/admins") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to "correct horse battery staple",
                    "roleIds" to listOf(roleId),
                ),
            )
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString

        val json = objectMapper.readTree(body)
        val roleIds = json.get("roles").let { r -> (0 until r.size()).map { r.get(it).get("id").asString() } }
        assertThat(roleIds).containsExactly(roleId)
        // The response reports what they can now do, not an empty list to be
        // filled in by a second request.
        assertThat(strings(json.get("permissions"))).contains("user.read")
    }

    @Test
    fun `an unknown role id creates no administrator at all`() {
        // The reason create and assign share a transaction. Assigning after the
        // account was saved would leave an administrator who exists and holds
        // nothing - a working sign-in that can do nothing, which fails quietly.
        val su = superToken()
        val unique = "rollback-${System.nanoTime()}"

        mockMvc.post("/api/v1/admin/admins") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to "correct horse battery staple",
                    "roleIds" to listOf(UUID.randomUUID().toString()),
                ),
            )
        }.andExpect { status { isNotFound() } }

        // Nothing was left behind: the address is still free.
        mockMvc.post("/api/v1/admin/admins") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to "correct horse battery staple",
                ),
            )
        }.andExpect { status { isCreated() } }
    }

}
