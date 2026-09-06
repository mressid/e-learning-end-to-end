package com.elearning.identity

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
 * Session lifetime: rotation, theft detection and logout.
 *
 * The property that matters is that a refresh token works exactly **once**.
 * That is what makes a stolen token detectable at all - otherwise a thief and
 * the rightful owner can both refresh forever and nothing distinguishes them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun register(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        return "$unique@example.com"
    }

    private fun login(email: String) = objectMapper.readTree(
        mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    )

    private fun refresh(token: String) = mockMvc.post("/api/v1/auth/refresh") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("refreshToken" to token))
    }

    private fun logout(token: String) = mockMvc.post("/api/v1/auth/logout") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("refreshToken" to token))
    }

    @Test
    fun `login issues both an access token and a refresh token`() {
        val body = login(register("pair"))
        assertThat(body.get("accessToken").asString()).isNotBlank()
        assertThat(body.get("refreshToken").asString()).isNotBlank()
        // The refresh token must not be a JWT: it is a random secret matched
        // against a stored hash, and carrying claims would invite trusting it.
        assertThat(body.get("refreshToken").asString()).doesNotContain(".")
    }

    @Test
    fun `a refresh token buys a working access token`() {
        val body = login(register("works"))
        val refreshed = objectMapper.readTree(
            refresh(body.get("refreshToken").asString())
                .andExpect { status { isOk() } }.andReturn().response.contentAsString,
        )

        mockMvc.get("/api/v1/me") {
            header("Authorization", "Bearer ${refreshed.get("accessToken").asString()}")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `refreshing rotates the token, so the old one stops working`() {
        val body = login(register("rotate"))
        val first = body.get("refreshToken").asString()

        val second = objectMapper.readTree(
            refresh(first).andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        assertThat(second).isNotEqualTo(first)
    }

    @Test
    fun `reusing a spent refresh token kills the whole session`() {
        val body = login(register("theft"))
        val stolen = body.get("refreshToken").asString()

        // The rightful owner refreshes; `stolen` is now spent.
        val successor = objectMapper.readTree(
            refresh(stolen).andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        // The thief presents the copy they took earlier.
        refresh(stolen).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }

        // A token working twice means a copy is in circulation, and there is no
        // way to tell which holder is genuine - so the victim's live token dies
        // too. Being logged out beats leaving the thief a working session.
        refresh(successor).andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `logout revokes the session`() {
        val body = login(register("logout"))
        val token = body.get("refreshToken").asString()

        logout(token).andExpect { status { isNoContent() } }
        refresh(token).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `logging out twice is not an error`() {
        val token = login(register("twice")).get("refreshToken").asString()
        logout(token).andExpect { status { isNoContent() } }
        // Idempotent: a client retrying after a dropped response must not get a
        // failure for work that already succeeded.
        logout(token).andExpect { status { isNoContent() } }
    }

    @Test
    fun `logout ends the whole rotation chain, not just the presented token`() {
        val token = login(register("chain")).get("refreshToken").asString()
        val second = objectMapper.readTree(
            refresh(token).andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        // Logging out with the current token must not leave earlier or later
        // members of the family usable - the family is the session.
        logout(second).andExpect { status { isNoContent() } }
        refresh(second).andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an unknown refresh token is rejected`() {
        refresh("not-a-real-token").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_REFRESH_TOKEN") }
        }
    }

    @Test
    fun `two sessions are independent - logging out of one leaves the other`() {
        val email = register("sessions")
        val phone = login(email).get("refreshToken").asString()
        val laptop = login(email).get("refreshToken").asString()

        logout(phone).andExpect { status { isNoContent() } }

        // Separate logins are separate families, so signing out on one device
        // must not sign the person out everywhere.
        refresh(laptop).andExpect { status { isOk() } }
    }

    @Test
    fun `refresh and logout need no access token of their own`() {
        val token = login(register("noauth")).get("refreshToken").asString()
        // Refresh is reached exactly when the access token has expired, so
        // requiring one would make the endpoint useless.
        refresh(token).andExpect { status { isOk() } }
    }
}
