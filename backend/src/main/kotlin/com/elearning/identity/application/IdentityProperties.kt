package com.elearning.identity.application

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "elearning.identity")
data class IdentityProperties(
    /**
     * Whether a new account must confirm its address before it can sign in.
     *
     * Defaults to **on**: an unverified address means the person who owns it
     * never agreed to anything, and the platform sends them mail. Deployments
     * that establish identity some other way can turn it off, in which case
     * accounts are created ACTIVE and no verification mail is sent.
     */
    val requireEmailVerification: Boolean = true,

    /** Where the links in verification and reset emails point. */
    val appBaseUrl: String = "http://localhost:3000",
)
