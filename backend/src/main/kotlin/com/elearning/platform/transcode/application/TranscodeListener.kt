package com.elearning.platform.transcode.application

import com.elearning.platform.transcode.TranscodeMessaging
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Subscribes the worker process to the transcode queue.
 *
 * Conditional, so only a process with an encoder consumes: an API replica that
 * took a job off this queue would fail it for want of ffmpeg and dead-letter
 * work a worker could have done.
 *
 * Thin on purpose. [TranscodeWorker] holds the logic and is a plain bean, so
 * tests exercise it directly instead of racing a live listener.
 */
@Component
@ConditionalOnProperty(name = ["elearning.transcode.enabled"], havingValue = "true")
class TranscodeListener(private val worker: TranscodeWorker) {

    @RabbitListener(queues = [TranscodeMessaging.QUEUE])
    fun onTranscodeRequested(rawJobId: String) = worker.process(rawJobId)
}
