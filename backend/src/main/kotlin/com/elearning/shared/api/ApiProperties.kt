package com.elearning.shared.api

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "elearning.api")
data class ApiProperties(
    /** Base path all versioned endpoints hang off, e.g. `/api/v1`. */
    val basePath: String = "/api/v1",
)
