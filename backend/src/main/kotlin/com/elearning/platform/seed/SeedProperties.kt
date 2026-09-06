package com.elearning.platform.seed

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "elearning.seed")
data class SeedProperties(
    /**
     * Seeding is idempotent and never deletes, so it is safe to leave on. Turn
     * it off where reference data is managed by hand and the file would fight
     * an operator's edits.
     */
    val enabled: Boolean = true,

    /** Spring resource location, so a deployment can point at a file on disk. */
    val location: String = "classpath:seed/reference-data.json",
)
