package com.elearning.assessment.api

import com.elearning.assessment.application.AddQuestionCommand
import com.elearning.assessment.application.OptionCommand
import com.elearning.assessment.application.QuizAttemptService
import com.elearning.assessment.application.QuizAuthoringService
import com.elearning.assessment.application.QuizGradingService
import com.elearning.assessment.application.ResponseGrade
import com.elearning.assessment.application.SaveQuizCommand
import com.elearning.assessment.application.SubmittedAnswer
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.ReorderRequest
import com.elearning.shared.errors.ApiError
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Quizzes", description = "Quiz authoring and attempts")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class QuizController(
    private val authoring: QuizAuthoringService,
    private val attemptService: QuizAttemptService,
    private val gradingService: QuizGradingService,
    private val currentUser: CurrentUser,
) {

    // ---- authoring (course editors) --------------------------------------

    @PutMapping("/items/{itemId}/quiz")
    @Operation(summary = "Create or replace the quiz on a QUIZ course item")
    fun saveQuiz(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: SaveQuizRequest,
    ): QuizResponseDto = QuizResponseDto.of(
        authoring.upsertQuiz(
            itemId,
            SaveQuizCommand(
                title = request.title,
                instructions = request.instructions,
                passingScore = request.passingScore,
                maxAttempts = request.maxAttempts,
                timeLimitSeconds = request.timeLimitSeconds,
                randomizeQuestions = request.randomizeQuestions,
            ),
            editorId = currentUser.requireId(),
        ),
    )

    @PostMapping("/items/{itemId}/quiz/questions")
    @Operation(summary = "Append a question")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Added"),
        ApiResponse(
            responseCode = "422",
            description = "Options missing, contradictory, or supplied for a text question",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun addQuestion(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: AddQuestionRequest,
    ): ResponseEntity<AuthorQuestionResponse> {
        val question = authoring.addQuestion(
            itemId,
            AddQuestionCommand(
                type = request.type,
                text = request.text,
                points = request.points,
                options = request.options.map { OptionCommand(it.text, it.isCorrect) },
            ),
            editorId = currentUser.requireId(),
        )
        val saved = authoring.questionsForEditor(itemId, currentUser.requireId())
            .first { it.question.id == question.id }
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthorQuestionResponse.of(saved))
    }

    @GetMapping("/items/{itemId}/quiz/questions")
    @Operation(
        summary = "List questions with their correct answers",
        description = "Editors only — this is the answer key.",
    )
    fun questions(@PathVariable itemId: UUID): List<AuthorQuestionResponse> =
        authoring.questionsForEditor(itemId, currentUser.requireId()).map(AuthorQuestionResponse::of)

    @PutMapping("/items/{itemId}/quiz/questions/order")
    @Operation(
        summary = "Reorder a quiz's questions",
        description = "Send every question id in the order you want. Partial orders are rejected.",
    )
    fun reorderQuestions(
        @PathVariable itemId: UUID,
        @Valid @RequestBody request: ReorderRequest,
    ): List<AuthorQuestionResponse> {
        authoring.reorderQuestions(itemId, request.orderedIds, currentUser.requireId())
        return authoring.questionsForEditor(itemId, currentUser.requireId()).map(AuthorQuestionResponse::of)
    }

    // ---- taking (enrolled students) --------------------------------------

    @PostMapping("/items/{itemId}/quiz/attempts")
    @Operation(
        summary = "Start (or resume) an attempt",
        description = "Resumes an unexpired attempt in progress rather than consuming another.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Attempt open; correct answers are not included"),
        ApiResponse(
            responseCode = "422",
            description = "No attempts left, or the quiz has no questions",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Not enrolled in this course",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun startAttempt(@PathVariable itemId: UUID): ResponseEntity<AttemptResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(
            AttemptResponse.of(attemptService.start(itemId, currentUser.requireId())),
        )

    @GetMapping("/attempts/{attemptId}")
    @Operation(summary = "Read one of your own attempts")
    fun attempt(@PathVariable attemptId: UUID): AttemptResponse =
        AttemptResponse.of(attemptService.get(attemptId, currentUser.requireId()))

    // ---- manual grading (course editors) ---------------------------------

    @GetMapping("/attempts/{attemptId}/grading")
    @Operation(
        summary = "Written answers awaiting a mark",
        description = "Course editors only. Choice questions are already graded and are not listed.",
    )
    fun grading(@PathVariable attemptId: UUID): AttemptGradingResponse =
        AttemptGradingResponse.of(gradingService.pendingGrading(attemptId, currentUser.requireId()))

    @PostMapping("/attempts/{attemptId}/grade")
    @Operation(
        summary = "Mark the written answers and finish the attempt",
        description = "Every written answer must be marked; a partial grade would understate the result.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Graded"),
        ApiResponse(
            responseCode = "422",
            description = "Answers still unmarked, points out of range, or an auto-graded question targeted",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun gradeAttempt(
        @PathVariable attemptId: UUID,
        @Valid @RequestBody request: GradeAttemptRequest,
    ): AttemptResultResponse = AttemptResultResponse.of(
        gradingService.grade(
            attemptId,
            request.grades.map { ResponseGrade(it.responseId, it.pointsAwarded) },
            editorId = currentUser.requireId(),
        ),
    )

    @PostMapping("/attempts/{attemptId}/submit")
    @Operation(
        summary = "Submit answers and grade the attempt",
        description = """
            Choice questions are graded immediately. A quiz containing free-text
            questions is left SUBMITTED for a human to mark, with no score, since
            scoring it now would ignore the written answers.
        """,
    )
    fun submit(
        @PathVariable attemptId: UUID,
        @Valid @RequestBody request: SubmitAttemptRequest,
    ): AttemptResultResponse = AttemptResultResponse.of(
        attemptService.submit(
            attemptId,
            currentUser.requireId(),
            request.answers.map { SubmittedAnswer(it.questionId, it.selectedOptionIds, it.answerText) },
        ),
    )
}
