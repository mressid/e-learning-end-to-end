package com.elearning.platform.notifications.api

import com.elearning.platform.notifications.Notification
import com.elearning.platform.notifications.NotificationService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@Schema(name = "NotificationResponse")
data class NotificationResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String?,
    val data: Map<String, Any>,
    val readAt: Instant?,
    val createdAt: Instant,
) {
    companion object {
        fun of(n: Notification) = NotificationResponse(
            id = requireNotNull(n.id),
            type = n.type,
            title = n.title,
            body = n.body,
            data = n.data,
            readAt = n.readAt,
            createdAt = n.createdAt,
        )
    }
}

@Schema(name = "UnreadCountResponse")
data class UnreadCountResponse(val unread: Long)

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "The caller's in-app notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class NotificationController(
    private val notifications: NotificationService,
    private val currentUser: CurrentUser,
) {

    @GetMapping("/me/notifications")
    @Operation(summary = "List your notifications, newest first")
    fun list(
        @RequestParam(defaultValue = "false") unreadOnly: Boolean,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<NotificationResponse> = PageResponse.from(
        notifications.list(
            currentUser.requireId(),
            unreadOnly,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        ),
        NotificationResponse::of,
    )

    @GetMapping("/me/notifications/unread-count")
    @Operation(summary = "How many are unread", description = "For a badge, without fetching the list.")
    fun unreadCount(): UnreadCountResponse =
        UnreadCountResponse(notifications.unreadCount(currentUser.requireId()))

    @PostMapping("/notifications/{notificationId}/read")
    @Operation(summary = "Mark one as read")
    fun markRead(@PathVariable notificationId: UUID): NotificationResponse =
        NotificationResponse.of(notifications.markRead(notificationId, currentUser.requireId()))

    @PostMapping("/me/notifications/read-all")
    @Operation(summary = "Mark everything as read")
    fun markAllRead(): UnreadCountResponse {
        notifications.markAllRead(currentUser.requireId())
        return UnreadCountResponse(notifications.unreadCount(currentUser.requireId()))
    }
}
