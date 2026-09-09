package com.elearning.courses.api

import com.elearning.courses.application.CourseService
import com.elearning.courses.application.CourseStructureService
import com.elearning.courses.application.CreateCourseCommand
import com.elearning.courses.application.TaxonomyService
import com.elearning.courses.application.UpdateCourseCommand
import com.elearning.courses.domain.Course
import com.elearning.platform.media.MediaService
import com.elearning.shared.api.OpenApiConfig
import com.elearning.shared.api.PageResponse
import com.elearning.shared.api.ReorderRequest
import com.elearning.shared.errors.ApiError
import com.elearning.shared.security.CurrentActor
import com.elearning.shared.security.CurrentUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/courses")
@Tag(name = "Courses", description = "Course authoring and discovery")
class CourseController(
    private val courseService: CourseService,
    private val structureService: CourseStructureService,
    private val taxonomyService: TaxonomyService,
    private val mediaService: MediaService,
    private val currentUser: CurrentUser,
    private val currentActor: CurrentActor,
) {

    @GetMapping
    @Operation(
        summary = "List published courses",
        description = "Public. Pass `q` to full-text search titles and descriptions.",
    )
    fun list(
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Max 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<CourseResponse> {
        // Search results come back ranked, so an extra sort would fight the ranking.
        val results = if (q.isNullOrBlank()) {
            courseService.listPublished(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt")),
            )
        } else {
            courseService.searchPublished(q, PageRequest.of(page, size))
        }
        // One lookup for the whole page: resolving thumbnails per row would be
        // a classic N+1 on the busiest endpoint on the platform. Categories and
        // tags are batched the same way, for the same reason.
        val thumbnails = mediaService.publicUrlsFor(results.content.mapNotNull { it.thumbnailMediaId })
        val ids = results.content.mapNotNull { it.id }
        val categories = taxonomyService.categoriesFor(ids)
        val tags = taxonomyService.tagsFor(ids)
        return PageResponse.from(results) {
            CourseResponse.of(
                it,
                thumbnails[it.thumbnailMediaId],
                categories[it.id].orEmpty(),
                tags[it.id].orEmpty(),
            )
        }
    }

    /*
     * Declared before `/{id}`, and matched before it regardless: Spring prefers
     * a literal segment to a template, so "mine" never reaches the UUID parser.
     */
    @GetMapping("/mine")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "List the courses you teach",
        description = "Everything you own or co-instruct, at any status. Unlike the public " +
            "listing this includes drafts, which is the point: an unfinished course is the " +
            "one its author still has work to do on. Authority here is the relationship, " +
            "not a permission - you are shown the courses your own id answers for.",
    )
    fun mine(
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @Parameter(description = "Max 100") @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): PageResponse<CourseResponse> {
        val results = courseService.listMine(
            currentUser.requireId(),
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        // Batched for the page, like the public listing: per-row lookups here
        // would be the same N+1 on a page an instructor opens constantly.
        val thumbnails = mediaService.publicUrlsFor(results.content.mapNotNull { it.thumbnailMediaId })
        val ids = results.content.mapNotNull { it.id }
        val categories = taxonomyService.categoriesFor(ids)
        val tags = taxonomyService.tagsFor(ids)
        return PageResponse.from(results) {
            CourseResponse.of(
                it,
                thumbnails[it.thumbnailMediaId],
                categories[it.id].orEmpty(),
                tags[it.id].orEmpty(),
            )
        }
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get a course",
        description = "Published courses are public. Drafts are visible only to the owner and its instructors.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Found"),
        ApiResponse(
            responseCode = "404",
            description = "No such course, or it is not published and you may not see it",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun get(@PathVariable id: UUID): CourseResponse = withThumbnail(
        courseService.getForViewer(id, currentUser.idOrNull()),
    )

    @PostMapping
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Create a course", description = "The caller becomes the owner. Starts as DRAFT.")
    fun create(@Valid @RequestBody request: CreateCourseRequest): ResponseEntity<CourseResponse> {
        val course = courseService.create(
            CreateCourseCommand(
                title = request.title,
                shortDescription = request.shortDescription,
                description = request.description,
                level = request.level,
                language = request.language,
                accessDurationDays = request.accessDurationDays,
            ),
            ownerId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(CourseResponse.of(course))
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Update a course")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Updated"),
        ApiResponse(
            responseCode = "403",
            description = "You do not own this course and are not one of its instructors",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateCourseRequest,
    ): CourseResponse = withThumbnail(
        courseService.update(
            id,
            UpdateCourseCommand(
                title = request.title,
                shortDescription = request.shortDescription,
                description = request.description,
                level = request.level,
                language = request.language,
                accessDurationDays = request.accessDurationDays,
            ),
            editorId = currentUser.requireId(),
        ),
    )

    @PostMapping("/{id}/publish")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Publish a course", description = "Requires at least one course item.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Published"),
        ApiResponse(
            responseCode = "422",
            description = "The course has no items yet",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun publish(@PathVariable id: UUID): CourseResponse =
        withThumbnail(courseService.publish(id, currentActor.requireId()))

    @PostMapping("/{id}/thumbnail")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Set the course thumbnail",
        description = "The image must be an upload made with visibility PUBLIC, so its URL is stable.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Thumbnail set"),
        ApiResponse(
            responseCode = "422",
            description = "The image is private or its upload is incomplete",
            content = [Content(schema = Schema(implementation = ApiError::class))],
        ),
    )
    fun setThumbnail(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SetThumbnailRequest,
    ): CourseResponse = withThumbnail(
        courseService.setThumbnail(id, request.mediaId, currentUser.requireId()),
    )

    @PutMapping("/{id}/sections/order")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Reorder a course's sections",
        description = "Send every section id in the order you want. Partial orders are rejected.",
    )
    fun reorderSections(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReorderRequest,
    ): List<SectionResponse> =
        structureService.reorderSections(id, request.orderedIds, currentUser.requireId())
            .map(SectionResponse::of)

    @PostMapping("/{id}/unpublish")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Return a course to draft",
        description = "Also the way back from ARCHIVED: archiving is undone by " +
            "returning the course to draft, then publishing it again.",
    )
    fun unpublish(@PathVariable id: UUID): CourseResponse =
        withThumbnail(courseService.unpublish(id, currentActor.requireId()))

    @PostMapping("/{id}/archive")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Retire a course",
        description = "Drops the course out of discovery and refuses new enrolments. " +
            "Students already enrolled keep their access and their progress - " +
            "retiring a course must not take away what someone already started. " +
            "An archived course cannot be published directly; it has to be " +
            "returned to draft first, so restoring one is a deliberate act.",
    )
    fun archive(@PathVariable id: UUID): CourseResponse =
        withThumbnail(courseService.archive(id, currentActor.requireId()))

    private fun withThumbnail(course: Course): CourseResponse {
        val url = course.thumbnailMediaId?.let { mediaService.publicUrlsFor(listOf(it))[it] }
        val ids = listOfNotNull(course.id)
        return CourseResponse.of(
            course,
            url,
            taxonomyService.categoriesFor(ids)[course.id].orEmpty(),
            taxonomyService.tagsFor(ids)[course.id].orEmpty(),
        )
    }

    // ---- taxonomy --------------------------------------------------------

    @PutMapping("/{id}/categories")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Set a course's categories",
        description = "Replaces the whole set, so sending an empty list clears them. " +
            "Categories are curated reference data - an unknown id is rejected " +
            "rather than created.",
    )
    fun setCategories(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SetCategoriesRequest,
    ): List<TermResponse> =
        taxonomyService.setCategories(id, request.categoryIds, currentUser.requireId())
            .map(TermResponse::of)

    @PutMapping("/{id}/tags")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Set a course's tags",
        description = "Replaces the whole set. Tags are matched and created by " +
            "slug, so \"Machine Learning\" and \"machine-learning\" are the same tag.",
    )
    fun setTags(
        @PathVariable id: UUID,
        @Valid @RequestBody request: SetTagsRequest,
    ): List<TermResponse> =
        taxonomyService.setTags(id, request.tags, currentUser.requireId()).map(TermResponse::of)

    // ---- structure -------------------------------------------------------

    @GetMapping("/{id}/sections")
    @Operation(summary = "List a course's sections, in order")
    fun sections(@PathVariable id: UUID): List<SectionResponse> =
        structureService.listSections(id, currentUser.idOrNull()).map(SectionResponse::of)

    @PostMapping("/{id}/sections")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(summary = "Append a section to a course")
    fun addSection(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CreateSectionRequest,
    ): ResponseEntity<SectionResponse> {
        val section = structureService.addSection(
            courseId = id,
            title = request.title,
            description = request.description,
            editorId = currentUser.requireId(),
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(SectionResponse.of(section))
    }
}
