package com.elearning.platform.email

import jakarta.validation.constraints.Email
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "elearning.mail")
data class EmailProperties(
    /** Envelope sender for every outbound message. */
    @field:Email val from: String = "no-reply@elearning.local",

    /**
     * Which implementation of [EmailSender] is active.
     *
     * `smtp` keeps Mailpit working locally with no credentials at all, so it
     * stays the default: a fresh clone must be able to run without signing up
     * for anything.
     */
    val provider: Provider = Provider.SMTP,

    val mailgun: Mailgun = Mailgun(),
) {
    enum class Provider { SMTP, MAILGUN }

    data class Mailgun(
        /** Mailgun's private API key. Sent as the HTTP Basic password. */
        val apiKey: String = "",
        /** The sending domain, e.g. `mg.example.com`. */
        val domain: String = "",
        /**
         * Regional host. Mailgun runs separate US and EU stacks and a domain
         * exists in exactly one of them - pointing at the wrong one returns
         * 401, which reads like a bad key rather than a wrong region.
         */
        val baseUrl: String = "https://api.mailgun.net",
    )
}
