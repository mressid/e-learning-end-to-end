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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Creating and listing instructors.
 *
 * An instructor is a kind of account, decided when it is created and permanent
 * afterwards. So the cases that matter are the one the old ownership-derived
 * roster could not express - somebody appointed before they have anything to
 * teach - and the one the flag that replaced it allowed by mistake: turning a
 * learner into an author with a PATCH.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminInstructorApiTest(
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

    private fun createUser(token: String, label: String, type: String): String {
        val unique = "$label-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to password,
                    "type" to type,
                ),
            )
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    // ---- creating ---------------------------------------------------------

    @Test
    fun `a super admin creates an instructor, and it is active immediately`() {
        val body = mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "teacher-${System.nanoTime()}@example.com",
                    "username" to "teacher-${System.nanoTime()}",
                    "password" to password,
                    "type" to "INSTRUCTOR",
                ),
            )
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val json = objectMapper.readTree(body)
        assertThat(json.get("type").asString()).isEqualTo("INSTRUCTOR")
        // Not PENDING: an administrator typing the address is the verification,
        // so there is no confirmation email to wait for.
        assertThat(json.get("status").asString()).isEqualTo("ACTIVE")
    }

    @Test
    fun `the account created can actually sign in`() {
        val unique = "signin-${System.nanoTime()}"
        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to password,
                    "type" to "INSTRUCTOR",
                ),
            )
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `creating needs user_write, and user_read is not enough`() {
        val token = adminWith("user.read")
        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "nope-${System.nanoTime()}@example.com",
                    "username" to "nope-${System.nanoTime()}",
                    "password" to password,
                ),
            )
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `a duplicate email is refused with a precise code`() {
        val su = superToken()
        val unique = "dupe-${System.nanoTime()}"
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "email" to "$unique@example.com",
                "username" to unique,
                "password" to password,
            ),
        )
        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = payload
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = payload
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_ALREADY_REGISTERED") }
        }
    }

    // ---- listing ----------------------------------------------------------

    @Test
    fun `an instructor with no courses still appears on the roster`() {
        // The whole point of appointing rather than deriving. The old
        // ownership-derived roster could not show this person at all.
        val su = superToken()
        val unique = "fresh-${System.nanoTime()}"
        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to password,
                    "type" to "INSTRUCTOR",
                ),
            )
        }.andExpect { status { isOk() } }

        val body = mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer $su")
            param("q", unique)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val json = objectMapper.readTree(body)
        assertThat(json.get("totalElements").asInt()).isEqualTo(1)
        val row = json.get("content").get(0)
        assertThat(row.get("username").asString()).isEqualTo(unique)
        assertThat(row.get("courseCount").asLong()).isZero()
        assertThat(row.get("publishedCourseCount").asLong()).isZero()
    }

    @Test
    fun `a student account is absent from the roster`() {
        val su = superToken()
        val id = createUser(su, "justalearner", type = "STUDENT")

        val body = mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer $su")
            param("size", "100")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val ids = objectMapper.readTree(body).get("content").let { c ->
            (0 until c.size()).map { c.get(it).get("id").asString() }
        }
        assertThat(ids).doesNotContain(id)
    }

    @Test
    fun `reading the roster needs user_read`() {
        val token = adminWith("audit.read")
        mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    // ---- updating ---------------------------------------------------------

    @Test
    fun `a student cannot be promoted into an instructor`() {
        // The flag this replaced made "instructor" a setting. Sending the field
        // now changes nothing: it is not part of the update contract, so it is
        // ignored rather than honoured, and the roster still does not list them.
        val su = superToken()
        val id = createUser(su, "promoted", type = "STUDENT")

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"isInstructor":true,"type":"INSTRUCTOR"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.type") { value("STUDENT") }
        }

        val body = mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer $su")
            param("size", "100")
        }.andReturn().response.contentAsString
        val ids = objectMapper.readTree(body).get("content").let { c ->
            (0 until c.size()).map { c.get(it).get("id").asString() }
        }
        assertThat(ids).doesNotContain(id)
    }

    @Test
    fun `an instructor cannot be demoted into a student`() {
        val su = superToken()
        val id = createUser(su, "demoted", type = "INSTRUCTOR")

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"type":"STUDENT"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.type") { value("INSTRUCTOR") }
        }
    }

    @Test
    fun `an unknown account type is refused rather than defaulted`() {
        val unique = "bogus-${System.nanoTime()}"
        mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${superToken()}")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to password,
                    "type" to "ADMIN",
                ),
            )
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_USER_TYPE") }
        }
    }

    @Test
    fun `an update touches only the fields sent`() {
        val su = superToken()
        val id = createUser(su, "partial", type = "INSTRUCTOR")
        val before = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/users/$id") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        )

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"firstName":"Amina"}"""
        }.andExpect { status { isOk() } }

        val after = objectMapper.readTree(
            mockMvc.get("/api/v1/admin/users/$id") {
                header("Authorization", "Bearer $su")
            }.andReturn().response.contentAsString,
        )
        assertThat(after.get("email").asString()).isEqualTo(before.get("email").asString())
        assertThat(after.get("username").asString()).isEqualTo(before.get("username").asString())
        // The kind of account is not in the update contract at all, so it is
        // unchanged for the same reason it is unchangeable.
        assertThat(after.get("type").asString()).isEqualTo("INSTRUCTOR")
    }

    // ---- resetting a lost password ---------------------------------------

    @Test
    fun `user_write sets an instructor's password and they sign in with it`() {
        // The case this exists for: an instructor an administrator created, who
        // has no working inbox for reset-by-email to reach.
        val su = superToken()
        val unique = "lostit-${System.nanoTime()}"
        val id = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/users") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "$unique@example.com",
                        "username" to unique,
                        "password" to password,
                        "type" to "INSTRUCTOR",
                    ),
                )
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        val replacement = "an entirely different passphrase"
        mockMvc.post("/api/v1/admin/users/$id/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${adminWith("user.read", "user.write")}")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to replacement))
        }.andExpect {
            status { isOk() }
            // Still an instructor: a reset changes the password, nothing else.
            jsonPath("$.type") { value("INSTRUCTOR") }
        }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$replacement"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `resetting ends the sessions the old password opened`() {
        val su = superToken()
        val unique = "stillin-${System.nanoTime()}"
        val id = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/users") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf(
                        "email" to "$unique@example.com",
                        "username" to unique,
                        "password" to password,
                        "type" to "INSTRUCTOR",
                    ),
                )
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("id").asString()

        val refresh = objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        mockMvc.post("/api/v1/admin/users/$id/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "yet another passphrase"))
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to refresh))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `resetting a password needs user_write, and user_read is not enough`() {
        val su = superToken()
        val id = createUser(su, "readonly", type = "INSTRUCTOR")

        mockMvc.post("/api/v1/admin/users/$id/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${adminWith("user.read")}")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "not mine to set"))
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `a password below the minimum is refused`() {
        val su = superToken()
        val id = createUser(su, "tooshort", type = "INSTRUCTOR")

        mockMvc.post("/api/v1/admin/users/$id/password") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = objectMapper.writeValueAsString(mapOf("newPassword" to "short"))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `updating needs user_write`() {
        val su = superToken()
        val id = createUser(su, "protected", type = "STUDENT")
        val token = adminWith("user.read")

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"firstName":"Nope"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }
}
