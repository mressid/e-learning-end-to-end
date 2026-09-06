package com.elearning.community.api

import com.elearning.community.application.CreateThreadCommand
import com.elearning.community.application.DiscussionService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.errors.ApiError
import com.elearning.shared.security.CurrentActor
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Discussions", description = "Course discussion threads and replies")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
class DiscussionController(
    private val discussions: DiscussionService,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @PostMapping("/courses/{courseId}/threads")
    @Operation(summary = "Start a thread", description = "Enrolled students and course staff.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created"),
        ApiResponse(
            responseCode = "403",
            description = "Not enrolled and not teaching this course",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun createThread(
        @PathVariable courseId: UUID,
        @Valid @RequestBody request: CreateThreadRequest,
    ): ResponseEntity<ThreadResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        ThreadResponse.of(
            discussions.createThread(
                courseId,
                CreateThreadCommand(request.title, request.body, request.lessonId),
                authorId = currentUser.requireId(),
            ),
        ),
    )

    @GetMapping("/courses/{courseId}/threads")
    @Operation(summary = "List threads", description = "Pass `lessonId` for one lesson's threads.")
    fun listThreads(
        @PathVariable courseId: UUID,
        @RequestParam(required = false) lessonId: UUID?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<ThreadResponse> = PageResponse.from(
        discussions.listThreads(
            courseId,
            lessonId,
            currentUser.requireId(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")),
        ),
        ThreadResponse::of,
    )

    @GetMapping("/threads/{threadId}")
    @Operation(summary = "Read a thread with its replies")
    fun thread(@PathVariable threadId: UUID): ThreadDetailResponse {
        val detail = discussions.getThread(threadId, currentUser.requireId())
        return ThreadDetailResponse(
            thread = ThreadResponse.of(detail.thread),
            comments = detail.comments.map(CommentResponse::of),
        )
    }

    @PostMapping("/threads/{threadId}/comments")
    @Operation(summary = "Reply to a thread or to another reply")
    fun addComment(
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: AddCommentRequest,
    ): ResponseEntity<CommentResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        CommentResponse.of(
            discussions.addComment(threadId, request.body, request.parentId, currentUser.requireId()),
        ),
    )

    @PostMapping("/threads/{threadId}/status")
    @Operation(
        summary = "Change a thread's status",
        description = "The author may mark answered or close; only course staff may hide.",
    )
    fun changeStatus(
        @PathVariable threadId: UUID,
        @Valid @RequestBody request: ChangeThreadStatusRequest,
    ): ThreadResponse = ThreadResponse.of(
        discussions.changeStatus(threadId, request.status, currentActor.requireId()),
    )
}
