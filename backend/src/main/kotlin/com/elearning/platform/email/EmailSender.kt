package com.elearning.platform.email

/**
 * Sending mail, as the application sees it (§22).
 *
 * Business code never touches JavaMail or a provider SDK, so swapping SMTP for
 * a hosted API is one class.
 */
interface EmailSender {
    fun send(to: String, subject: String, body: String)
}
