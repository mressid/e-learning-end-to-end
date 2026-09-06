package com.elearning.platform.notifications

import com.elearning.shared.events.AssignmentGraded
import com.elearning.shared.events.CertificateIssued
import com.elearning.shared.events.CourseCompleted
import com.elearning.shared.events.QuizGraded
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * Turns business events into notifications.
 *
 * Runs AFTER_COMMIT: a notification about work that was rolled back would be a
 * lie, and sending mail inside the business transaction would hold it open for
 * the length of an SMTP round trip.
 *
 * Deliberately NOT transactional itself. [NotificationService.create] manages its
 * own transaction, so by the time [notify] publishes, the notification row is
 * committed and the worker can find it. Wrapping these methods in a transaction
 * put the publish *inside* it, and the worker - which is fast - regularly won the
 * race and discarded the message as "unknown notification", losing the email.
 */
@Component
class NotificationEventListener(
    private val notificationService: NotificationService,
    private val publisher: NotificationPublisher,
) {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCourseCompleted(event: CourseCompleted) {
        notify(
            NotifyCommand(
                userId = event.studentId,
                type = "COURSE_COMPLETED",
                title = "You finished the course",
                body = "Congratulations on completing your course.",
                data = mapOf("courseId" to event.courseId.toString()),
                channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
            ),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAssignmentGraded(event: AssignmentGraded) {
        notify(
            NotifyCommand(
                userId = event.studentId,
                type = "ASSIGNMENT_GRADED",
                title = "Your assignment has been graded",
                data = mapOf(
                    "courseItemId" to event.courseItemId.toString(),
                    "submissionId" to event.submissionId.toString(),
                ),
                channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
            ),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onQuizGraded(event: QuizGraded) {
        notify(
            NotifyCommand(
                userId = event.studentId,
                type = "QUIZ_GRADED",
                title = "Your quiz has been marked",
                body = if (event.passed) "You passed." else "You did not reach the pass mark this time.",
                data = mapOf(
                    "courseItemId" to event.courseItemId.toString(),
                    "attemptId" to event.attemptId.toString(),
                ),
                channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
            ),
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onCertificateIssued(event: CertificateIssued) {
        notify(
            NotifyCommand(
                userId = event.studentId,
                type = "CERTIFICATE_ISSUED",
                title = "Your certificate is ready",
                body = "Certificate ${event.certificateNumber} has been issued.",
                data = mapOf(
                    "courseId" to event.courseId.toString(),
                    "certificateNumber" to event.certificateNumber,
                ),
                channels = setOf(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
            ),
        )
    }

    /** Create (and commit) first, publish second: order matters here. */
    private fun notify(command: NotifyCommand) {
        val notification = notificationService.create(command)
        publisher.requestDispatch(requireNotNull(notification.id))
    }
}
