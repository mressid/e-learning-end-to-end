package com.elearning.learning.application

import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.LearningProgress
import com.elearning.learning.domain.Lesson
import com.elearning.learning.domain.ProgressStatus
import com.elearning.learning.infrastructure.EnrollmentRepository
import com.elearning.learning.infrastructure.LessonRepository
import com.elearning.learning.infrastructure.LearningProgressRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.events.CertificateIssued
import com.elearning.shared.events.CourseCompleted
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Recording progress and deciding when a course counts as finished.
 */
@Service
class ProgressService(
    private val progress: LearningProgressRepository,
    private val lessons: LessonRepository,
    private val enrollments: EnrollmentRepository,
    private val enrollmentService: EnrollmentService,
    private val catalog: CourseCatalog,
    private val certificateService: CertificateService,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    fun record(itemId: UUID, studentId: UUID, command: RecordProgressCommand): LearningProgress {
        val courseId = catalog.courseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")

        // Authorization is by enrolment, not by a role: progress may only be
        // recorded against a course the student is actively enrolled in.
        val enrollment = enrollmentService.requireActiveEnrollment(courseId, studentId)

        command.progressPercent?.let {
            if (it < BigDecimal.ZERO || it > HUNDRED) {
                throw BusinessRuleException("INVALID_PROGRESS", "Progress must be between 0 and 100")
            }
        }

        requireItemUnlocked(itemId, studentId)

        val record = progress.findByStudentIdAndCourseItemId(studentId, itemId)
            .orElseGet {
                progress.save(
                    LearningProgress(studentId = studentId, courseId = courseId, courseItemId = itemId),
                )
            }

        // What the lesson's own rule says they have reached, which is not
        // always what they claim to have reached.
        val status = effectiveStatus(lessons.findById(itemId).orElse(null), command, record)
        record.record(status, command.progressPercent, command.lastPositionSeconds)
        enrollment.markStarted()

        // Finishing the last required item finishes the course.
        if (record.isCompleted && !enrollment.isCompleted && summarise(courseId, studentId).isComplete) {
            enrollment.complete()

            // Issued in this transaction so completion and certificate commit
            // together; the announcements go out only once that has happened.
            val certificate = certificateService.issue(studentId, courseId)
            events.publishEvent(CourseCompleted(studentId, courseId, requireNotNull(enrollment.id)))
            events.publishEvent(
                CertificateIssued(
                    studentId,
                    courseId,
                    requireNotNull(certificate.id),
                    certificate.certificateNumber,
                ),
            )
        }
        return record
    }

    /**
     * The status a lesson's completion rule allows, given what was reported.
     *
     * Only a lesson carries a rule. A quiz completes by being passed and an
     * assignment by being graded, both of which arrive here already decided,
     * so an item with no lesson row is taken at its word.
     *
     * The rule was stored and read by nothing until now: whatever a client
     * called itself, it was believed. An author who chose "reaching the end of
     * the video" got a lesson a student could finish by opening it.
     */
    private fun effectiveStatus(
        lesson: Lesson?,
        command: RecordProgressCommand,
        record: LearningProgress,
    ): ProgressStatus {
        if (lesson == null || command.status == ProgressStatus.NOT_STARTED) return command.status

        return when (lesson.completionRule) {
            CompletionRule.MANUAL -> command.status

            // Reaching it at all is the requirement, so reporting anything
            // finishes it - including the first "I have started this".
            CompletionRule.VIEW -> ProgressStatus.COMPLETED

            CompletionRule.DURATION -> {
                val runtime = lesson.durationSeconds
                val reached = command.lastPositionSeconds ?: record.lastPositionSeconds ?: 0
                // A lesson whose author stated no runtime has no bar to clear.
                // Holding it permanently incomplete would be a worse answer
                // than trusting the student.
                if (runtime == null || reached >= runtime) command.status else ProgressStatus.IN_PROGRESS
            }
        }
    }

    /**
     * Refuses progress on an item whose prerequisites are unfinished.
     *
     * Enforced here rather than at the controller because this is the single
     * chokepoint: a quiz pass and an assignment grade both complete their item
     * through this same use case, so a gate anywhere else would be bypassable
     * by finishing the quiz instead of watching the video.
     */
    private fun requireItemUnlocked(itemId: UUID, studentId: UUID) {
        val required = catalog.prerequisiteItemIds(itemId)
        if (required.isEmpty()) return

        val completed = progress.findByStudentIdAndCourseItemIdIn(studentId, required)
            .filter { it.isCompleted }
            .map { it.courseItemId }
            .toSet()

        if (!completed.containsAll(required)) {
            throw BusinessRuleException(
                "ITEM_LOCKED",
                "Finish this item's prerequisites first",
            )
        }
    }

    @Transactional(readOnly = true)
    fun courseProgress(courseId: UUID, studentId: UUID): CourseProgressSummary {
        enrollments.findByStudentIdAndCourseId(studentId, courseId)
            .orElseThrow { NotFoundException("ENROLLMENT_NOT_FOUND", "You are not enrolled in this course") }
        return summarise(courseId, studentId)
    }

    private fun summarise(courseId: UUID, studentId: UUID): CourseProgressSummary {
        val requiredIds = catalog.requiredItemIds(courseId).toSet()
        val records = progress.findByStudentIdAndCourseId(studentId, courseId)
        val completedRequired = records.count { it.isCompleted && it.courseItemId in requiredIds }

        val percent = if (requiredIds.isEmpty()) {
            BigDecimal.ZERO
        } else {
            BigDecimal(completedRequired)
                .multiply(HUNDRED)
                .divide(BigDecimal(requiredIds.size), 2, RoundingMode.DOWN)
        }

        return CourseProgressSummary(
            courseId = courseId,
            requiredItems = requiredIds.size,
            completedItems = completedRequired,
            percentComplete = percent,
            // An empty course is never "complete": there is nothing to have done.
            isComplete = requiredIds.isNotEmpty() && completedRequired == requiredIds.size,
            items = records,
        )
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}

data class RecordProgressCommand(
    val status: ProgressStatus,
    val progressPercent: BigDecimal? = null,
    val lastPositionSeconds: Int? = null,
)

data class CourseProgressSummary(
    val courseId: UUID,
    val requiredItems: Int,
    val completedItems: Int,
    val percentComplete: BigDecimal,
    val isComplete: Boolean,
    val items: List<LearningProgress>,
)
