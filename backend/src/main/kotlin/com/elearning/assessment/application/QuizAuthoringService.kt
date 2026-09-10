package com.elearning.assessment.application

import com.elearning.assessment.domain.Question
import com.elearning.assessment.domain.QuestionOption
import com.elearning.assessment.domain.QuestionType
import com.elearning.assessment.domain.Quiz
import com.elearning.assessment.infrastructure.QuestionOptionRepository
import com.elearning.assessment.infrastructure.QuestionRepository
import com.elearning.assessment.infrastructure.QuizAttemptRepository
import com.elearning.assessment.infrastructure.QuizRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.util.UUID

/**
 * Building a quiz. Authoring is for course editors only.
 */
@Service
class QuizAuthoringService(
    private val quizzes: QuizRepository,
    private val questions: QuestionRepository,
    private val options: QuestionOptionRepository,
    private val attempts: QuizAttemptRepository,
    private val context: LearningContext,
) {

    /**
     * A quiz's settings, for the screen that edits them.
     *
     * [upsertQuiz] replaces every field it is given, so a client that saves
     * without reading first would blank whatever it did not think to send.
     *
     * Carries whether the quiz has been sat, because that is what decides
     * whether half the editor is still usable. Without it the only way to find
     * out is to attempt a save and be refused, which teaches the author the
     * rule one rejected edit at a time.
     */
    @Transactional(readOnly = true)
    fun quizForEditor(itemId: UUID, editorId: UUID): QuizWithActivity {
        requireEditor(itemId, editorId)
        val quiz = quizzes.findById(itemId)
            .orElseThrow { NotFoundException("QUIZ_NOT_FOUND", "Quiz not found") }
        return QuizWithActivity(quiz, attempts.existsByQuizId(itemId))
    }

    @Transactional
    fun upsertQuiz(itemId: UUID, command: SaveQuizCommand, editorId: UUID): QuizWithActivity {
        requireEditor(itemId, editorId)

        command.passingScore?.let {
            if (it < BigDecimal.ZERO || it > HUNDRED) {
                throw BusinessRuleException("INVALID_PASSING_SCORE", "Passing score must be between 0 and 100")
            }
        }

        val existing = quizzes.findById(itemId).orElse(null)
        val quiz = if (existing == null) {
            quizzes.save(
                Quiz(
                    courseItemId = itemId,
                    title = command.title,
                    instructions = command.instructions,
                    passingScore = command.passingScore,
                    maxAttempts = command.maxAttempts,
                    timeLimitSeconds = command.timeLimitSeconds,
                    randomizeQuestions = command.randomizeQuestions ?: false,
                ),
            )
        } else {
            existing.apply {
                title = command.title
                instructions = command.instructions
                passingScore = command.passingScore
                maxAttempts = command.maxAttempts
                timeLimitSeconds = command.timeLimitSeconds
                command.randomizeQuestions?.let { randomizeQuestions = it }
            }
        }
        return QuizWithActivity(quiz, existing != null && attempts.existsByQuizId(itemId))
    }

    /**
     * Appends a question, while the paper is still being written.
     *
     * Refused once the quiz has been sat, for the same reason as the rest.
     * Appending destroys nothing, so this is not the data-loss argument that
     * blocks a delete - it is that a score is a percentage of the paper it was
     * earned on. Add a seventh question and the students who sat six are
     * recorded against a quiz that no longer exists, next to a cohort marked
     * out of more. Nothing here can be un-added either, since deleting is
     * frozen too, so the half-open version leaves the author worse off.
     */
    @Transactional
    fun addQuestion(itemId: UUID, command: AddQuestionCommand, editorId: UUID): Question {
        requireEditor(itemId, editorId)
        val quiz = quizzes.findById(itemId)
            .orElseThrow { NotFoundException("QUIZ_NOT_FOUND", "Quiz not found") }
        requireUnattempted(itemId, "no question can be added")

        validateOptions(command.type, command.options)

        val question = questions.save(
            Question(
                quizId = quiz.courseItemId,
                type = command.type,
                text = command.text,
                points = command.points ?: BigDecimal.ONE,
                position = questions.maxPosition(quiz.courseItemId) + 1,
            ),
        )
        command.options.forEachIndexed { index, option ->
            options.save(
                QuestionOption(
                    questionId = requireNotNull(question.id),
                    text = option.text,
                    isCorrect = option.isCorrect,
                    position = index,
                ),
            )
        }
        return question
    }

    /**
     * Edits a question in place.
     *
     * Wording and points are always correctable - a typo in a question is not
     * something an author should have to live with. The answer key is not: the
     * options are rows, so replacing them deletes the ones students actually
     * clicked, and `quiz_responses.selected_option_id` would be nulled while
     * their recorded scores stood. The type is the same story, since it decides
     * how the answer is marked.
     */
    @Transactional
    fun updateQuestion(
        itemId: UUID,
        questionId: UUID,
        command: UpdateQuestionCommand,
        editorId: UUID,
    ): Question {
        requireEditor(itemId, editorId)
        val question = requireQuestionOf(itemId, questionId)

        val touchesAnswerKey = command.type != null || command.options != null
        if (touchesAnswerKey) {
            requireUnattempted(itemId, "its questions can no longer be re-marked")

            val type = command.type ?: question.type
            // A type change with no options sent is judged against the ones
            // already there, so switching a choice question to free text has to
            // say so by sending an empty list rather than silently dropping them.
            val replacements = command.options
                ?: options.findByQuestionIdOrderByPosition(questionId)
                    .map { OptionCommand(it.text, it.isCorrect) }
            validateOptions(type, replacements)

            if (command.options != null) {
                options.deleteByQuestionId(questionId)
                replacements.forEachIndexed { index, option ->
                    options.save(
                        QuestionOption(
                            questionId = questionId,
                            text = option.text,
                            isCorrect = option.isCorrect,
                            position = index,
                        ),
                    )
                }
            }
            question.type = type
        }

        command.text?.let { question.text = it }
        command.points?.let { question.points = it }
        return question
    }

    /**
     * Removes a question and closes the gap it leaves.
     *
     * Refused once the quiz has been attempted: `quiz_responses.question_id`
     * cascades, so this would take every answer any student ever gave with it
     * while leaving their graded attempts standing at a score that no longer
     * adds up. Same rule as deleting a course item a student has worked on.
     */
    @Transactional
    fun deleteQuestion(itemId: UUID, questionId: UUID, editorId: UUID) {
        requireEditor(itemId, editorId)
        val question = requireQuestionOf(itemId, questionId)
        requireUnattempted(itemId, "no question can be removed")

        options.deleteByQuestionId(questionId)
        questions.delete(question)

        // Positions stay a dense run from zero, which is what the reorder path
        // writes and what a client renumbering its own list will assume.
        questions.findByQuizIdOrderByPosition(itemId)
            .filter { it.id != questionId }
            .forEachIndexed { index, remaining -> remaining.position = index }
    }

    /** The path names the quiz, so another quiz's question is simply not there. */
    private fun requireQuestionOf(itemId: UUID, questionId: UUID): Question {
        val question = questions.findById(questionId)
            .orElseThrow { NotFoundException("QUESTION_NOT_FOUND", "Question not found") }
        if (question.quizId != itemId) {
            throw NotFoundException("QUESTION_NOT_FOUND", "Question not found")
        }
        return question
    }

    /**
     * The answer key freezes once anyone has sat the quiz.
     *
     * Coarser than it strictly needs to be, since one attempt locks every
     * question rather than the ones that were answered. That is deliberate:
     * marking two students by different keys makes their scores incomparable,
     * so freezing the whole quiz is the rule worth having.
     */
    private fun requireUnattempted(itemId: UUID, reason: String) {
        if (attempts.existsByQuizId(itemId)) {
            throw BusinessRuleException("QUIZ_HAS_ATTEMPTS", "Students have already sat this quiz, so $reason")
        }
    }

    /**
     * A choice question with no correct option can never be answered correctly,
     * and a single-answer question with several is contradictory. Catching this
     * at authoring time avoids un-passable quizzes reaching students.
     */
    private fun validateOptions(type: QuestionType, candidates: List<OptionCommand>) {
        if (!type.isAutoGradable) {
            if (candidates.isNotEmpty()) {
                throw BusinessRuleException(
                    "OPTIONS_NOT_ALLOWED",
                    "$type questions cannot have options",
                )
            }
            return
        }

        if (candidates.size < 2) {
            throw BusinessRuleException("OPTIONS_REQUIRED", "A choice question needs at least two options")
        }
        val correct = candidates.count { it.isCorrect }
        if (correct == 0) {
            throw BusinessRuleException("NO_CORRECT_OPTION", "At least one option must be correct")
        }
        if (type != QuestionType.MULTIPLE_CHOICE && correct > 1) {
            throw BusinessRuleException(
                "TOO_MANY_CORRECT_OPTIONS",
                "$type questions allow only one correct option",
            )
        }
    }

    /**
     * Reorders a quiz's questions. Same rule as course structure: the request
     * must be a permutation of the current set, and the deferrable
     * `(quiz_id, position)` constraint lets positions be rewritten in one go.
     */
    @Transactional
    fun reorderQuestions(itemId: UUID, orderedIds: List<UUID>, editorId: UUID): List<Question> {
        requireEditor(itemId, editorId)
        val current = questions.findByQuizIdOrderByPosition(itemId)

        if (orderedIds.size != orderedIds.toSet().size) {
            throw BusinessRuleException("DUPLICATE_IN_ORDER", "The order lists the same question twice")
        }
        if (current.mapNotNull { it.id }.toSet() != orderedIds.toSet()) {
            throw BusinessRuleException(
                "INCOMPLETE_ORDER",
                "The order must list every question exactly once",
            )
        }

        val byId = current.associateBy { requireNotNull(it.id) }
        orderedIds.forEachIndexed { index, id -> requireNotNull(byId[id]).position = index }
        return orderedIds.map { requireNotNull(byId[it]) }
    }

    @Transactional(readOnly = true)
    fun questionsForEditor(itemId: UUID, editorId: UUID): List<QuestionWithOptions> {
        requireEditor(itemId, editorId)
        val all = questions.findByQuizIdOrderByPosition(itemId)
        val byQuestion = options.findByQuestionIdIn(all.mapNotNull { it.id }).groupBy { it.questionId }
        return all.map { QuestionWithOptions(it, byQuestion[it.id].orEmpty().sortedBy { o -> o.position }) }
    }

    @Transactional(readOnly = true)
    fun requireQuiz(itemId: UUID): Quiz = quizzes.findById(itemId)
        .orElseThrow { NotFoundException("QUIZ_NOT_FOUND", "Quiz not found") }

    private fun requireEditor(itemId: UUID, editorId: UUID) {
        val courseId = context.courseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        if (context.itemType(itemId) != "QUIZ") {
            throw BusinessRuleException("NOT_A_QUIZ_ITEM", "This course item is not a quiz")
        }
        if (!context.canEditCourse(courseId, editorId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }
    }

    private companion object {
        val HUNDRED: BigDecimal = BigDecimal("100")
    }
}

/** A quiz plus the one fact about it that the settings alone do not carry. */
data class QuizWithActivity(val quiz: Quiz, val hasAttempts: Boolean)

data class SaveQuizCommand(
    val title: String,
    val instructions: String? = null,
    val passingScore: BigDecimal? = null,
    val maxAttempts: Int? = null,
    val timeLimitSeconds: Int? = null,
    val randomizeQuestions: Boolean? = null,
)

data class AddQuestionCommand(
    val type: QuestionType,
    val text: String,
    val points: BigDecimal? = null,
    val options: List<OptionCommand> = emptyList(),
)

/** Every field optional: null leaves what is already stored alone. */
data class UpdateQuestionCommand(
    val type: QuestionType? = null,
    val text: String? = null,
    val points: BigDecimal? = null,
    /** An empty list clears the options; null leaves them untouched. */
    val options: List<OptionCommand>? = null,
)

data class OptionCommand(val text: String, val isCorrect: Boolean)

data class QuestionWithOptions(val question: Question, val options: List<QuestionOption>)
