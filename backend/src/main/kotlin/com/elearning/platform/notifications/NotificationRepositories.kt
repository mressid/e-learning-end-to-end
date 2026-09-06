package com.elearning.platform.notifications

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface NotificationRepository : JpaRepository<Notification, UUID> {

    fun findByUserId(userId: UUID, pageable: Pageable): Page<Notification>

    fun findByUserIdAndReadAtIsNull(userId: UUID, pageable: Pageable): Page<Notification>

    fun countByUserIdAndReadAtIsNull(userId: UUID): Long

    /** One statement rather than loading every unread row to stamp it. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.readAt = :at where n.userId = :userId and n.readAt is null")
    fun markAllRead(@Param("userId") userId: UUID, @Param("at") at: Instant): Int
}

interface NotificationDeliveryRepository : JpaRepository<NotificationDelivery, UUID> {

    fun findByNotificationId(notificationId: UUID): List<NotificationDelivery>
}
