package com.elearning.assessment.application

import com.elearning.assessment.domain.AttemptStatus
import com.elearning.assessment.domain.Question
import com.elearning.assessment.domain.QuizAttempt
import com.elearning.assessment.domain.QuizResponse
import com.elearning.assessment.infrastructure.QuestionRepository
import com.elearning.assessment.infrastructure.QuizAttemptRepository
import com.elearning.assessment.infrastructure.QuizResponseRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.events.QuizGraded
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Marking the free-text answers a machine cannot grade.
 *
 * Without this an attempt containing a written question reaches SUBMITTED and
 * stays there forever: the auto-grader deliberately refuses to score it.
 */
@Service
class QuizGradingService(
    private val attempts: QuizAttemptRepository,
    private val responses: QuizResponseRepository,
    private val questions: QuestionRepository,
    private val authoring: QuizAuthoringService,
    private val context: LearningContext,
    private val events: ApplicationEventPublisher,
) {

    /** The instructor's view: written answers alongside the question asked. */
    @Transactional(readOnly = true)
    fun pendingGrading(attemptId: UUID, editorId: UUID): AttemptGradingView {
        val attempt = requireGradableAttempt(attemptId, editorId)
        val questionsById = questions.findByQuizIdOrderByPosition(attempt.quizId)
            .associateBy { requireNotNull(it.id) }

        val toGrade = responses.findByAttemptId(attemptId)
            .filter { questionsById[it.questionId]?.type?.isAutoGradable == false }
            .map { response ->
                val question = requireNotNull(questionsById[response.questionId])
                ResponseToGrade(
                    responseId = requireNotNull(response.id),
                    questionText = question.text,
                    maxPoints = question.points,
                    answerText = response.answerText,
                    pointsAwarded = response.pointsAwarded,
                )
            }
        return AttemptGradingView(attempt, toGrade)
    }

    @Transactional
    fun grade(attemptId: UUID, grades: List<ResponseGrade>, editorId: UUID): AttemptResult {
        val attempt = requireGradableAttempt(attemptId, editorId)
        if (attempt.status == AttemptStatus.IN_PROGRESS) {
            throw BusinessRuleException("ATTEMPT_NOT_SUBMITTED", "This attempt has not been submitted yet")
        }

        val allResponses = responses.findByAttemptId(attemptId).associateBy { requireNotNull(it.id) }
        val questionsById = questions.findByQuizIdOrderByPosition(attempt.quizId)
            .associateBy { requireNotNull(it.id) }

        grades.forEach { grade ->
            val response = allResponses[grade.responseId]
                ?: throw BusinessRuleException("UNKNOWN_RESPONSE", "A grade refers to an answer not in this attempt")
            val question = requireNotNull(questionsById[response.questionId])

            if (question.type.isAutoGradable) {
                // Letting an instructor overwrite a machine-graded answer would
                // silently diverge from the answer key.
                throw BusinessRuleException(
                    "NOT_MANUALLY_GRADED",
                    "Choice questions are graded automatically and cannot be overridden",
                )
            }
            if (grade.pointsAwarded < BigDecimal.ZERO || grade.pointsAwarded > question.points) {
                throw BusinessRuleException(
                    "INVALID_POINTS",
                    "Points must be between 0 and ${question.points}",
                )
            }
            response.pointsAwarded = grade.pointsAwarded
            response.isCorrect = grade.pointsAwarded >= question.points
        }

        val stillUngraded = allResponses.values.count {
            val question = questionsById[it.questionId]
            question != null && !question.type.isAutoGradable && it.pointsAwarded == null
        }
        if (stillUngraded > 0) {
            // Publishing a partial score would understate the student's result.
            throw BusinessRuleException(
                "GRADING_INCOMPLETE",
                "$stillUngraded written answer(s) still need a mark",
            )
        }

        val score = totalScore(questionsById.values, allResponses.values)
        attempt.grade(score)

        val quiz = authoring.requireQuiz(attempt.quizId)
        val passed = quiz.isPassing(score)
        if (passed) {
            context.markItemCompleted(attempt.quizId, attempt.studentId)
        }
        // Marking happens long after the student left; without this they would
        // have to poll to discover their result.
        events.publishEvent(QuizGraded(attempt.studentId, attempt.quizId, attemptId, passed))
        return AttemptResult(attempt, score = score, passed = passed, awaitingManualGrading = false)
    }

    /**
     * Sums points across every question, so a quiz mixing choice and written
     * answers is scored over the whole paper rather than one half of it.
     */
    private fun totalScore(allQuestions: Collection<Question>, allResponses: Collection<QuizResponse>): BigDecimal {
        val possible = allQuestions.fold(BigDecimal.ZERO) { acc, q -> acc.add(q.points) }
        if (possible <= BigDecimal.ZERO) return BigDecimal.ZERO
        val earned = allResponses.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.pointsAwarded ?: BigDecimal.ZERO) }
        return earned.multiply(HUNDRED).divide(possible, 2, RoundingMode.DOWN)
    }

    private fun requireGradableAttempt(attemptId: UUID, editorId: UUID): QuizAttempt {
        val attempt = attempts.findById(attemptId)
            .orElseThrow { NotFoundException("ATTEMPT_NOT_FOUND", "Attempt not found") }
        val courseId = context.courseIdOfItem(attempt.quizId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        if (!context.canEditCourse(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to grade this course")
        }
        return attempt
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}

data class ResponseGrade(val responseId: UUID, val pointsAwarded: BigDecimal)

data class ResponseToGrade(
    val responseId: UUID,
    val questionText: String,
    val maxPoints: BigDecimal,
    val answerText: String?,
    val pointsAwarded: BigDecimal?,
)

data class AttemptGradingView(val attempt: QuizAttempt, val responses: List<ResponseToGrade>)
