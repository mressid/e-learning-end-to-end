package com.elearning.community.api

import com.elearning.community.application.ReviewService
import com.elearning.community.application.WriteReviewCommand
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
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reviews", description = "Course ratings and reviews")
class ReviewController(
    private val reviews: ReviewService,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @GetMapping("/courses/{courseId}/reviews")
    @Operation(summary = "List published reviews", description = "Public.")
    fun list(
        @PathVariable courseId: UUID,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<ReviewResponse> = PageResponse.from(
        reviews.listPublished(courseId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
        ReviewResponse::of,
    )

    @GetMapping("/courses/{courseId}/reviews/summary")
    @Operation(summary = "Average rating and review count", description = "Public.")
    fun summary(@PathVariable courseId: UUID): RatingSummaryResponse =
        RatingSummaryResponse.of(reviews.summarise(courseId))

    @PostMapping("/courses/{courseId}/reviews")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Review a course", description = "Only students who enrolled. One review per course.")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created"),
        ApiResponse(
            responseCode = "409",
            description = "You already reviewed this course",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Never enrolled, or you teach this course",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun create(
        @PathVariable courseId: UUID,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ResponseEntity<ReviewResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        ReviewResponse.of(
            reviews.create(
                courseId,
                WriteReviewCommand(request.rating, request.title, request.body),
                studentId = currentUser.requireId(),
            ),
        ),
    )

    @PatchMapping("/reviews/{reviewId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Edit your own review")
    fun update(
        @PathVariable reviewId: UUID,
        @Valid @RequestBody request: WriteReviewRequest,
    ): ReviewResponse = ReviewResponse.of(
        reviews.update(
            reviewId,
            WriteReviewCommand(request.rating, request.title, request.body),
            studentId = currentUser.requireId(),
        ),
    )

    @PostMapping("/reviews/{reviewId}/moderate")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Moderate a review", description = "Course staff only.")
    fun moderate(
        @PathVariable reviewId: UUID,
        @Valid @RequestBody request: ModerateReviewRequest,
    ): ReviewResponse = ReviewResponse.of(
        reviews.moderate(reviewId, request.status, editorId = currentActor.requireId()),
    )
}
