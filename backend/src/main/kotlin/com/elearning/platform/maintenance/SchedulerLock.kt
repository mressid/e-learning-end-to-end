package com.elearning.platform.maintenance

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Stops two instances running the same scheduled job at once.
 *
 * Uses a PostgreSQL **transaction-scoped** advisory lock rather than a library
 * such as ShedLock: no dependency, no extra table, and — the important part —
 * a transaction-scoped lock is released automatically when the transaction ends,
 * including if the instance dies mid-sweep. A session-scoped lock
 * (`pg_try_advisory_lock`) would be wrong here: with a connection pool the
 * unlock can land on a different connection than the lock, leaking it forever.
 *
 * Callers must already be inside a transaction; the lock lives and dies with it.
 */
@Component
class SchedulerLock(private val jdbcTemplate: JdbcTemplate) {

    /**
     * Runs [action] only if this instance won the lock for [name].
     * Returns null when another instance holds it — that is a normal outcome,
     * not an error: the other instance is doing the work.
     */
    fun <T> ifNotRunningElsewhere(name: String, action: () -> T): T? {
        val acquired = jdbcTemplate.queryForObject(
            "select pg_try_advisory_xact_lock(?)",
            Boolean::class.java,
            keyFor(name),
        )
        return if (acquired == true) action() else null
    }

    /**
     * A stable 64-bit key from the job name (FNV-1a).
     *
     * `String.hashCode` is only 32 bits and collides readily; two jobs sharing a
     * key would serialise against each other for no reason. Public so a test can
     * contend for the same lock from another connection.
     */
    fun keyFor(name: String): Long =
        name.fold(FNV_OFFSET) { hash, char -> (hash xor char.code.toLong()) * FNV_PRIME }

    private companion object {
        const val FNV_OFFSET = -3750763034362895579L // 14695981039346656037 unsigned
        const val FNV_PRIME = 1099511628211L
    }
}
