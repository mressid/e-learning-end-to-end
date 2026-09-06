package com.elearning.identity

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.RecordingEmailConfiguration
import com.elearning.shared.testing.RecordingEmailSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Email verification and password reset, with the requirement switched **on**.
 *
 * The rest of the suite runs with it off, because every test needing an
 * authenticated caller registers and logs in; this class is where the real
 * flow - PENDING account, emailed link, login gate - is actually exercised.
 * It carries its own property source and mail recorder, so it pays for one
 * extra Spring context. That is the deliberate trade: one context instead of a
 * verification click threaded through fifteen unrelated helpers.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(RecordingEmailConfiguration::class)
@TestPropertySource(properties = ["elearning.identity.require-email-verification=true"])
class EmailVerificationApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val emails: RecordingEmailSender,
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

    /** Pulls the token out of the most recent mail, the way a person clicks it. */
    private fun linkTokenFor(email: String, marker: String): String {
        val body = emails.sentTo(email).last { marker in it.body }.body
        return Regex("token=([A-Za-z0-9_-]+)").find(body)!!.groupValues[1]
    }

    private fun login(email: String, pass: String = password) = mockMvc.post("/api/v1/auth/login") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("email" to email, "password" to pass))
    }

    // ---- verification ----------------------------------------------------

    @Test
    fun `a new account starts pending and cannot sign in`() {
        val email = register("pending")

        login(email).andExpect {
            // Not 401: the caller has already proved they hold the password, so
            // naming the reason tells them nothing they could not establish,
            // and "invalid email or password" would be a dead end after signup.
            status { isForbidden() }
            jsonPath("$.code") { value("EMAIL_NOT_VERIFIED") }
        }
    }

    @Test
    fun `a wrong password on a pending account is still just invalid credentials`() {
        val email = register("pendingwrong")
        // The pending state is only disclosed once the password is right;
        // otherwise this endpoint would report which addresses are registered.
        login(email, "the wrong password entirely").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("INVALID_CREDENTIALS") }
        }
    }

    @Test
    fun `clicking the emailed link activates the account`() {
        val email = register("verify")
        val token = linkTokenFor(email, "verify-email")

        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to token))
        }.andExpect { status { isNoContent() } }

        val body = login(email).andExpect { status { isOk() } }.andReturn().response.contentAsString
        mockMvc.get("/api/v1/me") {
            header("Authorization", "Bearer ${objectMapper.readTree(body).get("accessToken").asString()}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACTIVE") }
        }
    }

    @Test
    fun `a second click is told the address is already verified`() {
        val email = register("twice")
        val token = linkTokenFor(email, "verify-email")
        fun verify() = mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to token))
        }

        verify().andExpect { status { isNoContent() } }
        // Distinguished from a forged link on purpose: a double click is a
        // normal thing for a person to do and deserves a truthful answer.
        verify().andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ALREADY_VERIFIED") }
        }
    }

    @Test
    fun `a forged token is refused`() {
        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to "made-up"))
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_VERIFICATION_TOKEN") }
        }
    }

    @Test
    fun `resending invalidates the previous link`() {
        val email = register("resend")
        val first = linkTokenFor(email, "verify-email")

        mockMvc.post("/api/v1/auth/verify-email/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email))
        }.andExpect { status { isAccepted() } }

        val second = linkTokenFor(email, "verify-email")
        assertThat(second).isNotEqualTo(first)

        // Two live links for one address means a leaked older one still works
        // after the newer has been used. Only the newest is honoured.
        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to first))
        }.andExpect { status { isUnprocessableEntity() } }
    }

    @Test
    fun `resending to an unknown address still reports success`() {
        // Anything else makes an unauthenticated endpoint a membership oracle.
        mockMvc.post("/api/v1/auth/verify-email/resend") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"nobody-${System.nanoTime()}@example.com"}"""
        }.andExpect { status { isAccepted() } }
    }

    // ---- password reset --------------------------------------------------

    private fun verified(label: String): String {
        val email = register(label)
        mockMvc.post("/api/v1/auth/verify-email") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to linkTokenFor(email, "verify-email")))
        }.andExpect { status { isNoContent() } }
        return email
    }

    private fun requestReset(email: String) = mockMvc.post("/api/v1/auth/password-reset") {
        contentType = MediaType.APPLICATION_JSON
        content = objectMapper.writeValueAsString(mapOf("email" to email))
    }

    @Test
    fun `a reset link sets a new password and the old one stops working`() {
        val email = verified("reset")
        requestReset(email).andExpect { status { isAccepted() } }
        val token = linkTokenFor(email, "reset-password")
        val newPassword = "an entirely different passphrase"

        mockMvc.post("/api/v1/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("token" to token, "newPassword" to newPassword),
            )
        }.andExpect { status { isNoContent() } }

        login(email).andExpect { status { isUnauthorized() } }
        login(email, newPassword).andExpect { status { isOk() } }
    }

    @Test
    fun `a reset link works only once`() {
        val email = verified("resetonce")
        requestReset(email).andExpect { status { isAccepted() } }
        val token = linkTokenFor(email, "reset-password")

        fun confirm(pass: String) = mockMvc.post("/api/v1/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("token" to token, "newPassword" to pass))
        }

        confirm("the first replacement passphrase").andExpect { status { isNoContent() } }
        // A link that stays usable is a standing takeover of the account for
        // as long as it sits in an inbox.
        confirm("a second replacement passphrase").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_RESET_TOKEN") }
        }
    }

    @Test
    fun `resetting the password signs out every existing session`() {
        val email = verified("resetsessions")
        val session = objectMapper.readTree(
            login(email).andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("refreshToken").asString()

        requestReset(email).andExpect { status { isAccepted() } }
        mockMvc.post("/api/v1/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("token" to linkTokenFor(email, "reset-password"), "newPassword" to "brand new passphrase here"),
            )
        }.andExpect { status { isNoContent() } }

        // Whoever prompted the reset may already hold a session. Leaving it
        // alive is the one outcome a reset exists to prevent.
        mockMvc.post("/api/v1/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("refreshToken" to session))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `requesting a reset for an unknown address reports success and sends nothing`() {
        val unknown = "ghost-${System.nanoTime()}@example.com"
        requestReset(unknown).andExpect { status { isAccepted() } }
        assertThat(emails.sentTo(unknown)).isEmpty()
    }

    @Test
    fun `a reset also settles a pending registration`() {
        val email = register("resetpending")
        requestReset(email).andExpect { status { isAccepted() } }
        val newPassword = "chosen at reset time instead"

        mockMvc.post("/api/v1/auth/password-reset/confirm") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf("token" to linkTokenFor(email, "reset-password"), "newPassword" to newPassword),
            )
        }.andExpect { status { isNoContent() } }

        // Receiving mail at the address proves control of it just as much as
        // clicking the verification link does.
        login(email, newPassword).andExpect { status { isOk() } }
    }
}
