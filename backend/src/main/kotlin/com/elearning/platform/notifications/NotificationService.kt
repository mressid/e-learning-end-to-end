package com.elearning.platform.notifications

import com.elearning.identity.infrastructure.UserRepository
import com.elearning.platform.email.EmailSender
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Notifications as an application capability (§23).
 *
 * ```
 * business event -> NotificationService -> channel provider
 * ```
 *
 * Business modules never name a channel implementation; they say what happened
 * and which channels are appropriate.
 */
@Service
class NotificationService(
    private val notifications: NotificationRepository,
    private val deliveries: NotificationDeliveryRepository,
    private val users: UserRepository,
    private val emailSender: EmailSender,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Records the notification and the delivery attempts it needs. IN_APP is
     * satisfied by the row itself; other channels are dispatched separately.
     *
     * REQUIRES_NEW because the caller is usually an AFTER_COMMIT event listener.
     * There, the completed transaction is still bound to the thread, so a plain
     * @Transactional would silently join a transaction that will never commit
     * and the row would vanish. A genuinely new transaction commits on return -
     * which is also what lets the caller publish a dispatch message afterwards
     * and be sure the worker can find the row.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun create(command: NotifyCommand): Notification {
        val notification = notifications.save(
            Notification(
                userId = command.userId,
                type = command.type,
                title = command.title,
                body = command.body,
                data = command.data.toMutableMap(),
            ),
        )
        command.channels.forEach { channel ->
            deliveries.save(NotificationDelivery(requireNotNull(notification.id), channel))
        }
        return notification
    }

    /**
     * Attempts every pending delivery for a notification.
     *
     * A channel failure is recorded, never thrown: one dead mail server must not
     * lose the in-app notification or fail the business operation behind it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun dispatch(notificationId: UUID) {
        val notification = notifications.findById(notificationId).orElse(null) ?: run {
            log.warn("Dispatch requested for unknown notification {}", notificationId)
            return
        }

        deliveries.findByNotificationId(notificationId)
            .filter { it.status == DeliveryStatus.PENDING }
            .forEach { delivery ->
                try {
                    when (delivery.channel) {
                        // The stored row is the in-app notification.
                        NotificationChannel.IN_APP -> delivery.markSent()
                        NotificationChannel.EMAIL -> {
                            val email = users.findById(notification.userId).orElse(null)?.email
                            if (email == null) {
                                delivery.markFailed("No email address for user")
                            } else {
                                emailSender.send(email, notification.title, notification.body.orEmpty())
                                delivery.markSent()
                            }
                        }
                        // No provider is wired; recorded as skipped rather than
                        // failed, since nothing was actually attempted.
                        NotificationChannel.PUSH -> {
                            delivery.status = DeliveryStatus.SKIPPED
                            delivery.failureReason = "Push channel is not configured"
                        }
                    }
                } catch (ex: Exception) {
                    log.warn("Delivery {} on {} failed", delivery.id, delivery.channel, ex)
                    delivery.markFailed(ex.message)
                }
            }
    }

    @Transactional(readOnly = true)
    fun list(userId: UUID, unreadOnly: Boolean, pageable: Pageable): Page<Notification> =
        if (unreadOnly) {
            notifications.findByUserIdAndReadAtIsNull(userId, pageable)
        } else {
            notifications.findByUserId(userId, pageable)
        }

    @Transactional(readOnly = true)
    fun unreadCount(userId: UUID): Long = notifications.countByUserIdAndReadAtIsNull(userId)

    @Transactional
    fun markRead(notificationId: UUID, userId: UUID): Notification {
        val notification = notifications.findById(notificationId)
            .orElseThrow { NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found") }
        if (notification.userId != userId) {
            throw ForbiddenException("NOTIFICATION_ACCESS_DENIED", "This notification is not yours")
        }
        notification.markRead()
        return notification
    }

    @Transactional
    fun markAllRead(userId: UUID): Int = notifications.markAllRead(userId, Instant.now())

    @Transactional(readOnly = true)
    fun deliveriesOf(notificationId: UUID): List<NotificationDelivery> =
        deliveries.findByNotificationId(notificationId)
}

data class NotifyCommand(
    val userId: UUID,
    val type: String,
    val title: String,
    val body: String? = null,
    val data: Map<String, Any> = emptyMap(),
    val channels: Set<NotificationChannel> = setOf(NotificationChannel.IN_APP),
)
