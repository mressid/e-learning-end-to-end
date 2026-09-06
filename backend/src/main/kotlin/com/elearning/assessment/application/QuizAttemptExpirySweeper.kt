package com.elearning.assessment.application

import com.elearning.assessment.domain.AttemptStatus
import com.elearning.assessment.infrastructure.QuizAttemptRepository
import com.elearning.assessment.infrastructure.QuizRepository
import com.elearning.platform.maintenance.MaintenanceProperties
import com.elearning.platform.maintenance.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

private const val LOCK_NAME = "quiz-attempt-expiry-sweep"

/**
 * Closes attempts whose time limit ran out and were never submitted.
 *
 * Expiry was previously only noticed when someone touched the attempt, so a
 * student who closed the tab left a row sitting IN_PROGRESS forever — which also
 * blocked them starting a fresh attempt, since an open attempt is resumed rather
 * than replaced.
 */
@Component
class QuizAttemptExpirySweeper(
    private val attempts: QuizAttemptRepository,
    private val quizzes: QuizRepository,
    private val properties: MaintenanceProperties,
    private val lock: SchedulerLock,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${elearning.maintenance.quiz-attempt-expiry-cron:0 10 * * * *}")
    @Transactional
    fun scheduledSweep() {
        if (!properties.enabled) return
        val expired = lock.ifNotRunningElsewhere(LOCK_NAME) { sweep() } ?: return
        if (expired > 0) log.info("Expired {} abandoned quiz attempts", expired)
    }

    /** Exposed so it can be run on demand and asserted on directly in tests. */
    @Transactional
    fun sweep(now: Instant = Instant.now()): Int {
        val open = attempts.findByStatus(
            AttemptStatus.IN_PROGRESS,
            PageRequest.of(0, properties.batchSize),
        )
        // Only quizzes with a time limit can expire; untimed attempts stay open.
        val limits = quizzes.findAllById(open.map { it.quizId })
            .associate { it.courseItemId to it.timeLimitSeconds }

        var expired = 0
        open.forEach { attempt ->
            if (attempt.hasExpired(limits[attempt.quizId], now)) {
                attempt.expire()
                expired++
            }
        }
        return expired
    }
}
