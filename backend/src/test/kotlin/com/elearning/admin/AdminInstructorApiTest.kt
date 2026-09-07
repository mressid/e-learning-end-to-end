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
 * Since V11 an instructor is a flag on the account rather than a consequence of
 * owning a course, so the interesting cases are the ones the old derivation
 * could not express: somebody enrolled before they have anything to teach, and
 * somebody whose flag is taken away while their courses stay theirs.
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

    private fun createUser(token: String, label: String, instructor: Boolean): String {
        val unique = "$label-${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/admin/users") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "$unique@example.com",
                    "username" to unique,
                    "password" to password,
                    "isInstructor" to instructor,
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
                    "isInstructor" to true,
                ),
            )
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val json = objectMapper.readTree(body)
        assertThat(json.get("isInstructor").asBoolean()).isTrue()
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
                    "isInstructor" to true,
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
        // The whole point of the flag. The old ownership-derived roster could
        // not show this person at all.
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
                    "isInstructor" to true,
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
    fun `a learner who is not flagged is absent from the roster`() {
        val su = superToken()
        val id = createUser(su, "justalearner", instructor = false)

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
    fun `promoting a learner puts them on the roster`() {
        val su = superToken()
        val id = createUser(su, "promoted", instructor = false)

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $su")
            content = """{"isInstructor":true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.isInstructor") { value(true) }
        }

        val body = mockMvc.get("/api/v1/admin/instructors") {
            header("Authorization", "Bearer $su")
            param("size", "100")
        }.andReturn().response.contentAsString
        val ids = objectMapper.readTree(body).get("content").let { c ->
            (0 until c.size()).map { c.get(it).get("id").asString() }
        }
        assertThat(ids).contains(id)
    }

    @Test
    fun `an update touches only the fields sent`() {
        val su = superToken()
        val id = createUser(su, "partial", instructor = true)
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
        // The flag was not in the payload, so it must not have been cleared.
        assertThat(after.get("isInstructor").asBoolean()).isTrue()
    }

    @Test
    fun `updating needs user_write`() {
        val su = superToken()
        val id = createUser(su, "protected", instructor = false)
        val token = adminWith("user.read")

        mockMvc.patch("/api/v1/admin/users/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"isInstructor":true}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }
}
