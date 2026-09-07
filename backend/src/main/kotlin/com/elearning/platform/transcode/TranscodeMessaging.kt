package com.elearning.platform.transcode

import org.springframework.amqp.core.Binding
import org.springframework.amqp.core.BindingBuilder
import org.springframework.amqp.core.Queue
import org.springframework.amqp.core.QueueBuilder
import org.springframework.amqp.core.TopicExchange
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Queue topology for transcoding, deliberately the same shape as
 * `NotificationMessaging`: a topic exchange, a durable queue, and a dead-letter
 * queue for work that fails every attempt.
 *
 * The listener is configured platform-wide with `default-requeue-rejected:
 * false`, which matters more here than for notifications - a video that kills
 * FFmpeg would otherwise be redelivered forever, and one bad upload would stop
 * every other lesson from being processed.
 */
@Configuration
class TranscodeMessaging {

    @Bean
    fun transcodeExchange(): TopicExchange = TopicExchange(EXCHANGE, true, false)

    @Bean
    fun transcodeQueue(): Queue = QueueBuilder.durable(QUEUE)
        .deadLetterExchange(EXCHANGE)
        .deadLetterRoutingKey(DEAD_LETTER_KEY)
        .build()

    @Bean
    fun transcodeDeadLetterQueue(): Queue = QueueBuilder.durable(DEAD_LETTER_QUEUE).build()

    @Bean
    fun transcodeBinding(): Binding =
        BindingBuilder.bind(transcodeQueue()).to(transcodeExchange()).with(REQUEST_KEY)

    @Bean
    fun transcodeDeadLetterBinding(): Binding =
        BindingBuilder.bind(transcodeDeadLetterQueue()).to(transcodeExchange()).with(DEAD_LETTER_KEY)

    companion object {
        const val EXCHANGE = "elearning.transcode"
        const val QUEUE = "elearning.transcode.requests"
        const val DEAD_LETTER_QUEUE = "elearning.transcode.requests.dlq"
        const val REQUEST_KEY = "transcode.request"
        const val DEAD_LETTER_KEY = "transcode.request.dead"
    }
}

/**
 * Hands a job to the workers.
 *
 * Only the id travels. The message is a pointer to committed state, not a copy
 * of it, so a worker always reads the row rather than trusting a payload that
 * may be a redelivery of something long since changed.
 */
@Component
class TranscodePublisher(private val rabbit: RabbitTemplate) {

    fun requestTranscode(jobId: UUID) {
        rabbit.convertAndSend(
            TranscodeMessaging.EXCHANGE,
            TranscodeMessaging.REQUEST_KEY,
            jobId.toString(),
        )
    }
}
