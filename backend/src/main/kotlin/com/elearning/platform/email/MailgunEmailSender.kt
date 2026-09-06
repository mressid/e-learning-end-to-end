package com.elearning.platform.email

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

/**
 * Sends through Mailgun's HTTP API.
 *
 * Chosen over Mailgun's SMTP endpoint because SMTP is frequently unavailable
 * where this will actually run: a lot of hosting blocks outbound 587 entirely,
 * and a blocked port surfaces as a connection timeout on the first password
 * reset rather than at deploy time. The HTTP path also returns a message id and
 * a readable error body, where SMTP gives a numeric code.
 *
 * The business modules see none of this - they hold [EmailSender] (§17/§22), so
 * switching providers is this one class and a property.
 */
@Component
@ConditionalOnProperty(name = ["elearning.mail.provider"], havingValue = "mailgun")
class MailgunEmailSender(
    builder: RestClient.Builder,
    private val properties: EmailProperties,
) : EmailSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val config = properties.mailgun

    init {
        // Fails at startup rather than at the first send. A missing key would
        // otherwise show up as an unverifiable account days later, and the
        // send path deliberately swallows failures so it would only be a log line.
        require(config.apiKey.isNotBlank()) {
            "elearning.mail.provider is mailgun but no API key is set (MAILGUN_API_KEY)"
        }
        require(config.domain.isNotBlank()) {
            "elearning.mail.provider is mailgun but no domain is set (MAILGUN_DOMAIN)"
        }
    }

    private val client = builder
        .baseUrl(config.baseUrl.trimEnd('/'))
        // Mailgun authenticates the literal username "api" with the key as the
        // password; the key is never a bearer token.
        .defaultHeaders { it.setBasicAuth("api", config.apiKey) }
        .build()

    override fun send(to: String, subject: String, body: String) {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("from", properties.from)
            add("to", to)
            add("subject", subject)
            add("text", body)
        }

        try {
            val response = client.post()
                .uri("/v3/{domain}/messages", config.domain)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(MailgunResponse::class.java)
            log.debug("Mailgun accepted message for {} ({})", to, response?.id)
        } catch (ex: RestClientResponseException) {
            // Mailgun's body says what is wrong - wrong region, unverified
            // sending domain, key without send rights - and the status alone
            // does not. Losing it turns every failure into "401".
            throw EmailDeliveryException(
                "Mailgun rejected the message for $to: ${ex.statusCode} ${ex.responseBodyAsString}",
                ex,
            )
        }
    }

    /** Mailgun answers with a queued-message id; only its presence matters here. */
    data class MailgunResponse(val id: String? = null, val message: String? = null)
}

/** Raised when a provider refuses a message. */
class EmailDeliveryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
