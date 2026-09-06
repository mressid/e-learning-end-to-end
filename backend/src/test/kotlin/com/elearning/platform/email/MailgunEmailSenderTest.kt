package com.elearning.platform.email

import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.util.Base64

/**
 * Pins the Mailgun wire format.
 *
 * No integration test can cover this - it would post real mail through a paid
 * account - so what is verified is the part that is easy to get wrong and
 * impossible to notice afterwards: the URL shape, the odd Basic-auth username,
 * the region host, and that a rejection is not swallowed.
 */
class MailgunEmailSenderTest {

    private fun sender(
        apiKey: String = "key-abc123",
        domain: String = "mg.example.com",
        baseUrl: String = "https://api.mailgun.net",
    ): Pair<MailgunEmailSender, MockRestServiceServer> {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val properties = EmailProperties(
            from = "no-reply@example.com",
            provider = EmailProperties.Provider.MAILGUN,
            mailgun = EmailProperties.Mailgun(apiKey = apiKey, domain = domain, baseUrl = baseUrl),
        )
        return MailgunEmailSender(builder, properties) to server
    }

    @Test
    fun `posts the message as a form to the domain's messages endpoint`() {
        val (sender, server) = sender()
        server.expect(requestTo("https://api.mailgun.net/v3/mg.example.com/messages"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(
                content().formData(
                    LinkedMultiValueMap<String, String>().apply {
                        add("from", "no-reply@example.com")
                        add("to", "learner@example.com")
                        add("subject", "Confirm your email address")
                        add("text", "a link")
                    },
                ),
            )
            .andRespond(withSuccess("""{"id":"<queued@mg>","message":"Queued"}""", MediaType.APPLICATION_JSON))

        sender.send("learner@example.com", "Confirm your email address", "a link")
        server.verify()
    }

    @Test
    fun `authenticates as the literal user api, with the key as the password`() {
        val (sender, server) = sender(apiKey = "key-secret")
        val expected = "Basic " + Base64.getEncoder().encodeToString("api:key-secret".toByteArray())

        // The key is the Basic *password*, never a bearer token, and the
        // username is always "api" rather than the domain. Both are easy to get
        // wrong and both fail as an indistinguishable 401.
        server.expect(requestTo("https://api.mailgun.net/v3/mg.example.com/messages"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, expected))
            .andRespond(withSuccess("""{"id":"<x>"}""", MediaType.APPLICATION_JSON))

        sender.send("learner@example.com", "s", "b")
        server.verify()
    }

    @Test
    fun `honours the EU host when configured`() {
        val (sender, server) = sender(baseUrl = "https://api.eu.mailgun.net")
        server.expect(requestTo("https://api.eu.mailgun.net/v3/mg.example.com/messages"))
            .andRespond(withSuccess("""{"id":"<x>"}""", MediaType.APPLICATION_JSON))

        sender.send("learner@example.com", "s", "b")
        server.verify()
    }

    @Test
    fun `a rejection carries the provider's explanation, not just the status`() {
        val (sender, server) = sender()
        server.expect(requestTo("https://api.mailgun.net/v3/mg.example.com/messages"))
            .andRespond(
                withStatus(HttpStatus.UNAUTHORIZED)
                    .body("""{"message":"Domain not found: mg.example.com"}""")
                    .contentType(MediaType.APPLICATION_JSON),
            )

        // The status alone cannot tell a wrong region from a bad key from an
        // unverified sending domain. Mailgun says which; the body must survive.
        assertThatThrownBy { sender.send("learner@example.com", "s", "b") }
            .isInstanceOf(EmailDeliveryException::class.java)
            .hasMessageContaining("Domain not found")
    }

    @Test
    fun `the configured from address is sent, not the recipient's domain`() {
        val (sender, server) = sender()
        server.expect(requestTo("https://api.mailgun.net/v3/mg.example.com/messages"))
            .andExpect(content().string(containsString("from=no-reply%40example.com")))
            .andRespond(withSuccess("""{"id":"<x>"}""", MediaType.APPLICATION_JSON))

        sender.send("learner@example.com", "s", "b")
        server.verify()
    }

    @Test
    fun `refuses to start without an api key`() {
        // Startup, not first send: the send path deliberately swallows failures
        // so registration cannot be lost to a bounce, which means a missing key
        // would otherwise surface days later as accounts nobody could verify.
        assertThatThrownBy { sender(apiKey = "") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MAILGUN_API_KEY")
    }

    @Test
    fun `refuses to start without a domain`() {
        assertThatThrownBy { sender(domain = "") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MAILGUN_DOMAIN")
    }
}
