package com.elearning.platform.notifications

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

/** Mirrors `notification_deliveries.channel`. */
enum class NotificationChannel { IN_APP, EMAIL, PUSH }

/** Mirrors `notification_deliveries.status`. */
enum class DeliveryStatus { PENDING, SENT, FAILED, SKIPPED }

/**
 * A logical notification. How it reaches the user is a separate concern,
 * recorded per channel in [NotificationDelivery] (§23).
 */
@Entity
@Table(name = "notifications")
class Notification(

    @Column(name = "user_id", nullable = false, updatable = false)
    val userId: UUID,

    @Column(nullable = false, updatable = false)
    val type: String,

    @Column(nullable = false)
    var title: String,

    @Column
    var body: String? = null,

    /**
     * Contextual payload as JSONB, so a new notification type carries its own
     * ids without a schema change.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var data: MutableMap<String, Any> = mutableMapOf(),
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "read_at")
    var readAt: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    val isRead: Boolean get() = readAt != null

    /** Idempotent: re-reading does not move the timestamp. */
    fun markRead(at: Instant = Instant.now()) {
        if (readAt == null) readAt = at
    }
}

@Entity
@Table(name = "notification_deliveries")
class NotificationDelivery(

    @Column(name = "notification_id", nullable = false, updatable = false)
    val notificationId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val channel: NotificationChannel,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: DeliveryStatus = DeliveryStatus.PENDING

    @Column(name = "sent_at")
    var sentAt: Instant? = null

    /** Why a channel failed, kept for support rather than shown to the user. */
    @Column(name = "failure_reason")
    var failureReason: String? = null

    fun markSent(at: Instant = Instant.now()) {
        status = DeliveryStatus.SENT
        sentAt = at
        failureReason = null
    }

    fun markFailed(reason: String?) {
        status = DeliveryStatus.FAILED
        // Column is unbounded text, but a stack trace in a column helps nobody.
        failureReason = reason?.take(500)
    }
}
