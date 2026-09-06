package com.elearning.identity.application

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generating and hashing the random secrets that stand in for a password:
 * refresh tokens, verification links, reset links.
 *
 * Every one of them is stored as a SHA-256 hex digest and never in the clear, so
 * a dump of those tables hands out nothing usable. SHA-256 rather than bcrypt is
 * deliberate, and the opposite of the choice made for `users.password_hash`:
 * these are 256-bit values the server generated, so there is no dictionary to
 * run against them and a slow KDF would buy nothing while costing real time on
 * every refresh.
 */
object SecureTokens {

    private val random = SecureRandom()

    /** 256 bits, URL-safe: these travel in JSON bodies and in email links. */
    fun random(): String {
        val bytes = ByteArray(32).also(random::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(rawToken: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(rawToken.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
