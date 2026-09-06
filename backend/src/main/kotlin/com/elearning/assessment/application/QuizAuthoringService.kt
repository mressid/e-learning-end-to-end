package com.elearning.assessment.application

import com.elearning.assessment.domain.Question
import com.elearning.assessment.domain.QuestionOption
import com.elearning.assessment.domain.QuestionType
import com.elearning.assessment.domain.Quiz
import com.elearning.assessment.infrastructure.QuestionOptionRepository
import com.elearning.assessment.infrastructure.QuestionRepository
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
    private val context: LearningContext,
) {

    @Transactional
    fun upsertQuiz(itemId: UUID, command: SaveQuizCommand, editorId: UUID): Quiz {
        requireEditor(itemId, editorId)

        command.passingScore?.let {
            if (it < BigDecimal.ZERO || it > HUNDRED) {
                throw BusinessRuleException("INVALID_PASSING_SCORE", "Passing score must be between 0 and 100")
            }
        }

        val existing = quizzes.findById(itemId).orElse(null)
        return if (existing == null) {
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
    }

    @Transactional
    fun addQuestion(itemId: UUID, command: AddQuestionCommand, editorId: UUID): Question {
        requireEditor(itemId, editorId)
        val quiz = quizzes.findById(itemId)
            .orElseThrow { NotFoundException("QUIZ_NOT_FOUND", "Quiz not found") }

        validateOptions(command)

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
     * A choice question with no correct option can never be answered correctly,
     * and a single-answer question with several is contradictory. Catching this
     * at authoring time avoids un-passable quizzes reaching students.
     */
    private fun validateOptions(command: AddQuestionCommand) {
        if (!command.type.isAutoGradable) {
            if (command.options.isNotEmpty()) {
                throw BusinessRuleException(
                    "OPTIONS_NOT_ALLOWED",
                    "${command.type} questions cannot have options",
                )
            }
            return
        }

        if (command.options.size < 2) {
            throw BusinessRuleException("OPTIONS_REQUIRED", "A choice question needs at least two options")
        }
        val correct = command.options.count { it.isCorrect }
        if (correct == 0) {
            throw BusinessRuleException("NO_CORRECT_OPTION", "At least one option must be correct")
        }
        if (command.type != QuestionType.MULTIPLE_CHOICE && correct > 1) {
            throw BusinessRuleException(
                "TOO_MANY_CORRECT_OPTIONS",
                "${command.type} questions allow only one correct option",
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

data class OptionCommand(val text: String, val isCorrect: Boolean)

data class QuestionWithOptions(val question: Question, val options: List<QuestionOption>)
