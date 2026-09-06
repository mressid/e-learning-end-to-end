package com.elearning.assessment.application

import com.elearning.assessment.domain.Assignment
import com.elearning.assessment.domain.AssignmentSubmission
import com.elearning.assessment.domain.SubmissionMedia
import com.elearning.assessment.domain.SubmissionMediaId
import com.elearning.assessment.infrastructure.AssignmentRepository
import com.elearning.assessment.infrastructure.AssignmentSubmissionRepository
import com.elearning.assessment.infrastructure.SubmissionMediaRepository
import com.elearning.platform.media.MediaService
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.events.AssignmentGraded
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Assignments: authoring, student submission, instructor grading.
 */
@Service
class AssignmentService(
    private val assignments: AssignmentRepository,
    private val submissions: AssignmentSubmissionRepository,
    private val submissionMedia: SubmissionMediaRepository,
    private val mediaService: MediaService,
    private val context: LearningContext,
    private val events: ApplicationEventPublisher,
) {

    @Transactional
    fun upsert(itemId: UUID, command: SaveAssignmentCommand, editorId: UUID): Assignment {
        requireEditor(itemId, editorId)
        if (command.maxScore <= BigDecimal.ZERO) {
            throw BusinessRuleException("INVALID_MAX_SCORE", "Maximum score must be greater than zero")
        }

        val existing = assignments.findById(itemId).orElse(null)
        return if (existing == null) {
            assignments.save(
                Assignment(
                    courseItemId = itemId,
                    instructions = command.instructions,
                    maxScore = command.maxScore,
                    dueAt = command.dueAt,
                    allowLateSubmission = command.allowLateSubmission ?: false,
                ),
            )
        } else {
            existing.apply {
                instructions = command.instructions
                maxScore = command.maxScore
                dueAt = command.dueAt
                command.allowLateSubmission?.let { allowLateSubmission = it }
            }
        }
    }

    @Transactional(readOnly = true)
    fun get(itemId: UUID, viewerId: UUID): Assignment {
        val courseId = requireAssignmentItem(itemId)
        if (!context.canEditCourse(courseId, viewerId)) {
            context.requireActiveEnrollment(courseId, viewerId)
        }
        return requireAssignment(itemId)
    }

    @Transactional
    fun submit(itemId: UUID, command: SubmitAssignmentCommand, studentId: UUID): AssignmentSubmission {
        val courseId = requireAssignmentItem(itemId)
        context.requireActiveEnrollment(courseId, studentId)
        val assignment = requireAssignment(itemId)

        if (!assignment.acceptsSubmissionAt()) {
            throw BusinessRuleException("SUBMISSION_LATE", "The due date has passed and late work is not accepted")
        }
        if (command.content.isNullOrBlank() && command.mediaIds.isEmpty()) {
            throw BusinessRuleException("EMPTY_SUBMISSION", "A submission needs text or at least one file")
        }

        val previous = submissions.countByAssignmentIdAndStudentId(itemId, studentId)
        val submission = submissions.save(
            AssignmentSubmission(
                assignmentId = itemId,
                studentId = studentId,
                attemptNumber = previous + 1,
                content = command.content,
            ),
        )

        command.mediaIds.forEach { mediaId ->
            val media = mediaService.requireAvailable(mediaId)
            // A student may only attach their own uploads: otherwise a guessed
            // media id would let them submit someone else's work.
            if (media.createdBy != studentId) {
                throw ForbiddenException("MEDIA_ACCESS_DENIED", "That file is not yours to submit")
            }
            submissionMedia.save(SubmissionMediaId(requireNotNull(submission.id), mediaId).let(::SubmissionMedia))
        }

        // Handing work in is progress, but not completion - that waits on a mark.
        context.markItemInProgress(itemId, studentId)
        return submission
    }

    @Transactional(readOnly = true)
    fun mySubmissions(itemId: UUID, studentId: UUID): List<AssignmentSubmission> {
        val courseId = requireAssignmentItem(itemId)
        context.requireActiveEnrollment(courseId, studentId)
        return submissions.findByAssignmentIdAndStudentIdOrderByAttemptNumberDesc(itemId, studentId)
    }

    @Transactional(readOnly = true)
    fun submissionsForEditor(itemId: UUID, editorId: UUID, pageable: Pageable): Page<AssignmentSubmission> {
        requireEditor(itemId, editorId)
        return submissions.findByAssignmentId(itemId, pageable)
    }

    @Transactional
    fun grade(submissionId: UUID, command: GradeSubmissionCommand, editorId: UUID): AssignmentSubmission {
        val submission = submissions.findById(submissionId)
            .orElseThrow { NotFoundException("SUBMISSION_NOT_FOUND", "Submission not found") }
        requireEditor(submission.assignmentId, editorId)

        val assignment = requireAssignment(submission.assignmentId)
        if (command.score < BigDecimal.ZERO || command.score > assignment.maxScore) {
            throw BusinessRuleException("INVALID_SCORE", "Score must be between 0 and ${assignment.maxScore}")
        }

        submission.grade(command.score, command.feedback, editorId)
        events.publishEvent(
            AssignmentGraded(submission.studentId, submission.assignmentId, submissionId),
        )
        // A graded assignment completes the item regardless of the mark: the
        // work was done and assessed. There is no pass mark in the V1 model.
        context.markItemCompleted(submission.assignmentId, submission.studentId)
        return submission
    }

    @Transactional(readOnly = true)
    fun mediaIdsOf(submissionId: UUID): List<UUID> =
        submissionMedia.findByIdSubmissionId(submissionId).map { it.id.mediaId }

    private fun requireAssignment(itemId: UUID): Assignment = assignments.findById(itemId)
        .orElseThrow { NotFoundException("ASSIGNMENT_NOT_FOUND", "Assignment not found") }

    private fun requireAssignmentItem(itemId: UUID): UUID {
        val courseId = context.courseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        if (context.itemType(itemId) != "ASSIGNMENT") {
            throw BusinessRuleException("NOT_AN_ASSIGNMENT_ITEM", "This course item is not an assignment")
        }
        return courseId
    }

    private fun requireEditor(itemId: UUID, editorId: UUID) {
        val courseId = requireAssignmentItem(itemId)
        if (!context.canEditCourse(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }
    }
}

data class SaveAssignmentCommand(
    val instructions: String? = null,
    val maxScore: BigDecimal = BigDecimal("100"),
    val dueAt: java.time.Instant? = null,
    val allowLateSubmission: Boolean? = null,
)

data class SubmitAssignmentCommand(
    val content: String? = null,
    val mediaIds: List<UUID> = emptyList(),
)

data class GradeSubmissionCommand(val score: BigDecimal, val feedback: String? = null)
