package com.elearning.platform.email

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * SMTP implementation. Mailpit locally, or any SMTP provider in production -
 * the difference is configuration, not code.
 *
 * The default, so a fresh clone runs with no credentials at all. Set
 * `elearning.mail.provider=mailgun` to send through Mailgun's HTTP API instead.
 */
@Component
@ConditionalOnProperty(
    name = ["elearning.mail.provider"],
    havingValue = "smtp",
    matchIfMissing = true,
)
class SmtpEmailSender(
    private val mailSender: JavaMailSender,
    private val properties: EmailProperties,
) : EmailSender {

    override fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage().apply {
            setFrom(properties.from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        mailSender.send(message)
    }
}
