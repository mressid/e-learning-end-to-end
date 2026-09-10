package com.elearning.assessment.application

import com.elearning.assessment.domain.Question
import com.elearning.assessment.domain.QuestionOption
import com.elearning.assessment.domain.QuestionType
import com.elearning.assessment.domain.Quiz
import com.elearning.assessment.domain.QuizAttempt
import com.elearning.assessment.domain.QuizResponse
import com.elearning.assessment.infrastructure.QuestionOptionRepository
import com.elearning.assessment.infrastructure.QuestionRepository
import com.elearning.assessment.infrastructure.QuizAttemptRepository
import com.elearning.assessment.infrastructure.QuizResponseRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Random
import java.util.UUID

/**
 * Taking and grading a quiz.
 *
 * The answer key never leaves this service: student-facing views are built from
 * [StudentQuestion], which has no `isCorrect` field at all, so it cannot be
 * leaked by forgetting to strip it.
 */
@Service
class QuizAttemptService(
    private val attempts: QuizAttemptRepository,
    private val responses: QuizResponseRepository,
    private val questions: QuestionRepository,
    private val options: QuestionOptionRepository,
    private val authoring: QuizAuthoringService,
    private val context: LearningContext,
) {

    @Transactional
    fun start(itemId: UUID, studentId: UUID): AttemptView {
        val quiz = authoring.requireQuiz(itemId)
        requireEnrolled(itemId, studentId)

        val questionList = questions.findByQuizIdOrderByPosition(itemId)
        if (questionList.isEmpty()) {
            throw BusinessRuleException("QUIZ_HAS_NO_QUESTIONS", "This quiz has no questions yet")
        }

        val previous = attempts.findByQuizIdAndStudentIdOrderByAttemptNumberDesc(itemId, studentId)

        // An abandoned attempt still counts against the limit, but reopening the
        // one in progress is what a student refreshing the page expects.
        previous.firstOrNull { it.isOpen }?.let { open ->
            if (open.hasExpired(quiz.timeLimitSeconds)) {
                open.expire()
            } else {
                return view(open, quiz, questionList)
            }
        }

        if (!quiz.allowsAnotherAttempt(previous.size)) {
            throw BusinessRuleException("NO_ATTEMPTS_LEFT", "You have used all attempts for this quiz")
        }

        val attempt = attempts.save(
            QuizAttempt(
                quizId = itemId,
                studentId = studentId,
                attemptNumber = previous.size + 1,
            ),
        )
        return view(attempt, quiz, questionList)
    }

    @Transactional(readOnly = true)
    fun get(attemptId: UUID, studentId: UUID): AttemptView {
        val attempt = requireOwnAttempt(attemptId, studentId)
        val quiz = authoring.requireQuiz(attempt.quizId)
        return view(attempt, quiz, questions.findByQuizIdOrderByPosition(attempt.quizId))
    }

    @Transactional
    fun submit(attemptId: UUID, studentId: UUID, answers: List<SubmittedAnswer>): AttemptResult {
        val attempt = requireOwnAttempt(attemptId, studentId)
        if (!attempt.isOpen) {
            throw BusinessRuleException("ATTEMPT_CLOSED", "This attempt has already been submitted")
        }

        val quiz = authoring.requireQuiz(attempt.quizId)
        if (attempt.hasExpired(quiz.timeLimitSeconds)) {
            attempt.expire()
            throw BusinessRuleException("ATTEMPT_EXPIRED", "The time limit for this attempt has passed")
        }

        val questionList = questions.findByQuizIdOrderByPosition(attempt.quizId)
        val byId = questionList.associateBy { requireNotNull(it.id) }
        val answerByQuestion = answers.associateBy { it.questionId }

        answers.requireAllKnown(byId)

        // Resubmission of the same attempt replaces its answers rather than
        // accumulating duplicates.
        responses.deleteByAttemptId(attemptId)

        val optionsByQuestion = options.findByQuestionIdIn(byId.keys).groupBy { it.questionId }
        var earned = BigDecimal.ZERO
        var autoGradable = BigDecimal.ZERO
        var needsManualGrading = false

        questionList.forEach { question ->
            val questionId = requireNotNull(question.id)
            val answer = answerByQuestion[questionId]
            val questionOptions = optionsByQuestion[questionId].orEmpty()

            if (question.type.isAutoGradable) {
                autoGradable = autoGradable.add(question.points)
                val correct = gradeChoice(question, questionOptions, answer)
                if (correct) earned = earned.add(question.points)
                writeChoiceResponses(attemptId, questionId, answer, correct, question.points)
            } else {
                needsManualGrading = true
                responses.save(
                    QuizResponse(
                        attemptId = attemptId,
                        questionId = questionId,
                        answerText = answer?.answerText,
                    ),
                )
            }
        }

        if (needsManualGrading) {
            // Scoring now would report a mark that ignores the written answers.
            attempt.submitAwaitingManualGrading()
            return AttemptResult(attempt, score = null, passed = null, awaitingManualGrading = true)
        }

        val score = if (autoGradable <= BigDecimal.ZERO) {
            BigDecimal.ZERO
        } else {
            earned.multiply(HUNDRED).divide(autoGradable, 2, RoundingMode.DOWN)
        }
        attempt.grade(score)

        val passed = quiz.isPassing(score)
        if (passed) {
            // Passing the quiz completes the course item it belongs to.
            context.markItemCompleted(attempt.quizId, studentId)
        }
        return AttemptResult(attempt, score = score, passed = passed, awaitingManualGrading = false)
    }

    /** Rejects an answer aimed at a question that is not in this quiz. */
    private fun List<SubmittedAnswer>.requireAllKnown(known: Map<UUID, Question>) {
        firstOrNull { it.questionId !in known }?.let {
            throw BusinessRuleException("UNKNOWN_QUESTION", "An answer refers to a question not in this quiz")
        }
    }

    private fun gradeChoice(
        question: Question,
        questionOptions: List<QuestionOption>,
        answer: SubmittedAnswer?,
    ): Boolean {
        val correctIds = questionOptions.filter { it.isCorrect }.mapNotNull { it.id }.toSet()
        val validIds = questionOptions.mapNotNull { it.id }.toSet()
        val selected = answer?.selectedOptionIds.orEmpty().toSet()

        // Options from another question are ignored rather than credited.
        val selectedHere = selected.intersect(validIds)
        if (selectedHere.isEmpty()) return false

        return when (question.type) {
            // Partial credit is not awarded: all correct options and no others.
            QuestionType.MULTIPLE_CHOICE -> selectedHere == correctIds
            else -> selectedHere.size == 1 && selectedHere.first() in correctIds
        }
    }

    private fun writeChoiceResponses(
        attemptId: UUID,
        questionId: UUID,
        answer: SubmittedAnswer?,
        correct: Boolean,
        points: BigDecimal,
    ) {
        val selected = answer?.selectedOptionIds.orEmpty()
        if (selected.isEmpty()) {
            responses.save(
                QuizResponse(attemptId = attemptId, questionId = questionId).apply {
                    isCorrect = false
                    pointsAwarded = BigDecimal.ZERO
                },
            )
            return
        }
        // One row per selected option, so a multi-select answer is fully recorded.
        selected.forEachIndexed { index, optionId ->
            responses.save(
                QuizResponse(
                    attemptId = attemptId,
                    questionId = questionId,
                    selectedOptionId = optionId,
                ).apply {
                    isCorrect = correct
                    pointsAwarded = if (correct && index == 0) points else BigDecimal.ZERO
                },
            )
        }
    }

    private fun requireOwnAttempt(attemptId: UUID, studentId: UUID): QuizAttempt {
        val attempt = attempts.findById(attemptId)
            .orElseThrow { NotFoundException("ATTEMPT_NOT_FOUND", "Attempt not found") }
        if (attempt.studentId != studentId) {
            throw ForbiddenException("ATTEMPT_ACCESS_DENIED", "This attempt is not yours")
        }
        return attempt
    }

    private fun requireEnrolled(itemId: UUID, studentId: UUID) {
        val courseId = context.courseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        context.requireActiveEnrollment(courseId, studentId)
    }

    private fun view(attempt: QuizAttempt, quiz: Quiz, questionList: List<Question>): AttemptView {
        val optionsByQuestion = options
            .findByQuestionIdIn(questionList.mapNotNull { it.id })
            .groupBy { it.questionId }

        val ordered = if (quiz.randomizeQuestions) questionList.shuffled(shuffleFor(attempt)) else questionList
        return AttemptView(
            attempt = attempt,
            quiz = quiz,
            questions = ordered.map { question ->
                StudentQuestion(
                    id = requireNotNull(question.id),
                    type = question.type,
                    text = question.text,
                    points = question.points,
                    options = optionsByQuestion[question.id].orEmpty()
                        .sortedBy { it.position }
                        // No isCorrect here, by construction.
                        .map { StudentOption(requireNotNull(it.id), it.text) },
                )
            },
        )
    }

    /**
     * The same shuffle every time this attempt is read.
     *
     * The order used to be drawn fresh on each call, and this runs on starting
     * an attempt *and* on reading one back - so a student who reloaded the page
     * mid-attempt found the questions rearranged under them, halfway through
     * answering them. Seeding from the attempt's own id keeps one sitting in a
     * fixed order without storing that order anywhere, while still handing two
     * students different papers.
     */
    private fun shuffleFor(attempt: QuizAttempt): Random {
        val id = requireNotNull(attempt.id)
        return Random(id.mostSignificantBits xor id.leastSignificantBits)
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}

data class SubmittedAnswer(
    val questionId: UUID,
    val selectedOptionIds: List<UUID> = emptyList(),
    val answerText: String? = null,
)

/** A question as a student may see it: no answer key. */
data class StudentQuestion(
    val id: UUID,
    val type: QuestionType,
    val text: String,
    val points: BigDecimal,
    val options: List<StudentOption>,
)

data class StudentOption(val id: UUID, val text: String)

data class AttemptView(
    val attempt: QuizAttempt,
    val quiz: Quiz,
    val questions: List<StudentQuestion>,
)

data class AttemptResult(
    val attempt: QuizAttempt,
    val score: BigDecimal?,
    val passed: Boolean?,
    val awaitingManualGrading: Boolean,
)
