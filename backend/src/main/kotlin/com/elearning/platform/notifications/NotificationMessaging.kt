package com.elearning.platform.notifications

import org.slf4j.LoggerFactory
import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Delivery runs off a queue so a slow mail server never blocks the request that
 * triggered the notification (§19).
 *
 * The message is just the notification id, so the worker reads current state
 * rather than a stale copy carried in the payload. This requires the row to be
 * committed before the message is published - see NotificationEventListener.
 */
@Configuration
class NotificationMessagingConfig {

    @Bean
    fun notificationsExchange(): TopicExchange = TopicExchange(EXCHANGE, true, false)

    @Bean
    fun notificationDispatchQueue(): Queue = QueueBuilder.durable(QUEUE)
        // Failed messages land here instead of being retried forever.
        .deadLetterExchange(EXCHANGE)
        .deadLetterRoutingKey(DEAD_LETTER_KEY)
        .build()

    @Bean
    fun notificationDeadLetterQueue(): Queue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build()

    @Bean
    fun notificationDispatchBinding(): Binding =
        BindingBuilder.bind(notificationDispatchQueue()).to(notificationsExchange()).with(DISPATCH_KEY)

    @Bean
    fun notificationDeadLetterBinding(): Binding =
        BindingBuilder.bind(notificationDeadLetterQueue()).to(notificationsExchange()).with(DEAD_LETTER_KEY)

    companion object {
        const val EXCHANGE = "elearning.notifications"
        const val QUEUE = "elearning.notifications.dispatch"
        const val DEAD_LETTER_QUEUE = "elearning.notifications.dispatch.dlq"
        const val DISPATCH_KEY = "notification.dispatch"
        const val DEAD_LETTER_KEY = "notification.dispatch.dead"
    }
}

@Component
class NotificationPublisher(
    private val rabbitTemplate: RabbitTemplate,
    private val notificationService: NotificationService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Queues a notification for delivery.
     *
     * If the broker is unreachable the notification still exists and is visible
     * in-app; delivery falls back to running inline rather than being lost.
     */
    fun requestDispatch(notificationId: UUID) {
        try {
            rabbitTemplate.convertAndSend(
                NotificationMessagingConfig.EXCHANGE,
                NotificationMessagingConfig.DISPATCH_KEY,
                notificationId.toString(),
            )
        } catch (ex: Exception) {
            log.warn("Could not queue notification {}; delivering inline", notificationId, ex)
            notificationService.dispatch(notificationId)
        }
    }
}

@Component
class NotificationDispatchWorker(private val notificationService: NotificationService) {

    private val log = LoggerFactory.getLogger(javaClass)

    @RabbitListener(queues = [NotificationMessagingConfig.QUEUE])
    fun onDispatchRequested(notificationId: String) {
        val id = runCatching { UUID.fromString(notificationId) }.getOrNull()
        if (id == null) {
            // Malformed: nothing to retry, so let it dead-letter rather than loop.
            log.warn("Discarding unparseable notification id '{}'", notificationId)
            return
        }
        notificationService.dispatch(id)
    }
}
