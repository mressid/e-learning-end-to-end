package com.elearning.platform.maintenance

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Housekeeping jobs.
 *
 * Everything is switchable and bounded: a maintenance job that cannot be turned
 * off, or that processes an unbounded batch, is a liability in production.
 */
@ConfigurationProperties(prefix = "elearning.maintenance")
data class MaintenanceProperties(
    val enabled: Boolean = true,
    /** How long an upload may sit unconfirmed before it is swept. */
    val pendingUploadGrace: Duration = Duration.ofHours(24),
    /** Upper bound on rows touched per run, so one pass cannot stall the app. */
    val batchSize: Int = 200,
)
