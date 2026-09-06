package com.elearning.shared.testing

import com.elearning.platform.email.EmailSender
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Captures outbound mail instead of talking to a server.
 *
 * There is no SMTP server in the test environment, and the behaviour under test
 * is the notification pipeline - that an email delivery is attempted and its
 * outcome recorded - not JavaMail's ability to speak SMTP.
 */
class RecordingEmailSender : EmailSender {

    private val sent = ConcurrentLinkedQueue<SentEmail>()
    private val failing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Fails only for one address.
     *
     * Deliberately not a global flag: delivery is asynchronous, so a message
     * produced by one test can be consumed after that test has finished. A
     * global switch would then make an unrelated test's mail fail. Scoping the
     * failure to a recipient removes the race.
     */
    fun failFor(address: String) {
        failing += address
    }

    override fun send(to: String, subject: String, body: String) {
        if (to in failing) throw IllegalStateException("simulated mail failure for $to")
        sent += SentEmail(to, subject, body)
    }

    fun sentTo(address: String): List<SentEmail> = sent.filter { it.to == address }

    fun clear() {
        sent.clear()
        failing.clear()
    }

    data class SentEmail(val to: String, val subject: String, val body: String)
}

@TestConfiguration
class RecordingEmailConfiguration {

    @Bean
    @Primary
    fun recordingEmailSender(): RecordingEmailSender = RecordingEmailSender()
}
