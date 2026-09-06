package com.elearning.platform.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * One thing that happened, and who did it.
 *
 * Immutable in practice: nothing in the application updates a row after it is
 * written, and no endpoint deletes one. A log that can be edited is not
 * evidence.
 */
@Entity
@Table(name = "audit_log")
class AuditEntry(

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    val actorType: ActorType,

    @Column(name = "actor_id")
    val actorId: UUID? = null,

    /**
     * Who the actor was at the time.
     *
     * Snapshotted rather than joined: resolving the name live would let a later
     * rename rewrite history, and would leave deleted accounts showing as bare
     * UUIDs exactly when the record matters most.
     */
    @Column(name = "actor_label", length = 320)
    val actorLabel: String? = null,

    @Column(nullable = false, length = 64)
    val action: String,

    @Column(name = "target_type", length = 32)
    val targetType: String? = null,

    @Column(name = "target_id")
    val targetId: UUID? = null,

    @Column(nullable = false, length = 500)
    val summary: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val details: MutableMap<String, Any> = mutableMapOf(),

    @Column(name = "request_id", length = 64)
    val requestId: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "occurred_at", nullable = false, updatable = false)
    var occurredAt: Instant = Instant.now()
}

enum class ActorType { ADMIN, USER, SYSTEM }
