package com.elearning.learning.application

import com.elearning.learning.domain.EnrollmentStatus
import com.elearning.learning.infrastructure.EnrollmentRepository
import com.elearning.platform.maintenance.MaintenanceProperties
import com.elearning.platform.maintenance.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Moves enrolments whose access window has passed from ACTIVE to EXPIRED.
 *
 * `Enrollment.isClosed()` already refuses access once `expires_at` is in the
 * past, so this is not what stops a student working - it is what makes the
 * stored status honest, so listings and reporting do not show a stale ACTIVE.
 *
 * **Nothing sets `expires_at` yet.** Access duration is a product decision (per
 * course? per enrolment? a platform default?), and inventing one would bake a
 * guess into the schema. Until that is decided this job is inert by design; the
 * mechanism is here so the policy is the only thing missing.
 */
private const val LOCK_NAME = "enrolment-expiry-sweep"

@Component
class EnrollmentExpirySweeper(
    private val enrollments: EnrollmentRepository,
    private val properties: MaintenanceProperties,
    private val lock: SchedulerLock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${elearning.maintenance.enrolment-expiry-cron:0 5 * * * *}")
    @Transactional
    fun scheduledSweep() {
        if (!properties.enabled) return
        val expired = lock.ifNotRunningElsewhere(LOCK_NAME) { sweep() } ?: return
        if (expired > 0) log.info("Expired {} enrolments", expired)
    }

    /** Exposed so it can be run on demand and asserted on directly in tests. */
    @Transactional
    fun sweep(now: Instant = Instant.now()): Int {
        val due = enrollments.findByStatusAndExpiresAtBefore(
            EnrollmentStatus.ACTIVE,
            now,
            PageRequest.of(0, properties.batchSize),
        )
        due.forEach { it.status = EnrollmentStatus.EXPIRED }
        return due.size
    }
}
