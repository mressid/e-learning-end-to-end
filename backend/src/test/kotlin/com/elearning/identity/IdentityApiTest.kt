package com.elearning.identity

import com.elearning.identity.infrastructure.UserRepository
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
 * Covers the registration/login/authenticated-read path end to end: HTTP in,
 * real PostgreSQL out, real JWT verification in between.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IdentityApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val users: UserRepository,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun registerBody(email: String, username: String) = """
        {"email":"$email","username":"$username","password":"$password",
         "firstName":"Jane","lastName":"Doe"}
    """.trimIndent()

    @Test
    fun `registers an account and stores the password hashed`() {
        val email = "register-${System.nanoTime()}@example.com"

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody(email, "user${System.nanoTime()}")
        }.andExpect {
            status { isCreated() }
            jsonPath("$.email") { value(email) }
            jsonPath("$.status") { value("ACTIVE") }
            // The response must never carry credentials back.
            jsonPath("$.passwordHash") { doesNotExist() }
        }

        val stored = users.findByEmailIgnoreCase(email).orElseThrow()
        assertThat(stored.passwordHash).doesNotContain(password)
        assertThat(stored.passwordHash).startsWith("{bcrypt}")
    }

    @Test
    fun `rejects a duplicate email with a precise error code`() {
        val email = "dupe-${System.nanoTime()}@example.com"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody(email, "first${System.nanoTime()}")
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody(email, "second${System.nanoTime()}")
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("EMAIL_ALREADY_REGISTERED") }
            jsonPath("$.requestId") { exists() }
        }
    }

    @Test
    fun `reports every invalid field at once`() {
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"nope","username":"x","password":"short"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors.length()") { value(3) }
        }
    }

    @Test
    fun `logs in and reads the account with the issued token`() {
        val email = "login-${System.nanoTime()}@example.com"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody(email, "login${System.nanoTime()}")
        }.andExpect { status { isCreated() } }

        val response = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        val token = objectMapper.readTree(response).get("accessToken").asString()

        mockMvc.get("/api/v1/me") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.email") { value(email) }
        }
    }

    @Test
    fun `wrong password and unknown account are indistinguishable`() {
        val email = "known-${System.nanoTime()}@example.com"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = registerBody(email, "known${System.nanoTime()}")
        }.andExpect { status { isCreated() } }

        val wrongPassword = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"definitely wrong"}"""
        }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        val unknownUser = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"ghost@example.com","password":"definitely wrong"}"""
        }.andExpect { status { isUnauthorized() } }.andReturn().response.contentAsString

        // Same code and message: the response must not reveal that an account exists.
        val code: (String) -> String = { objectMapper.readTree(it).get("code").asString() }
        assertThat(code(wrongPassword)).isEqualTo(code(unknownUser)).isEqualTo("INVALID_CREDENTIALS")
    }

    @Test
    fun `rejects an unauthenticated read with the standard error shape`() {
        mockMvc.get("/api/v1/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("UNAUTHORIZED") }
        }
    }

    @Test
    fun `rejects a tampered token`() {
        mockMvc.get("/api/v1/me") {
            header("Authorization", "Bearer not.a.real.token")
        }.andExpect { status { isUnauthorized() } }
    }
}
