package com.elearning.courses.api

import com.elearning.courses.application.TaxonomyService
import com.elearning.courses.domain.Category
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import com.elearning.shared.api.OpenApiConfig
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Schema(name = "CreateCategoryRequest")
data class CreateCategoryRequest(
    @field:NotBlank @field:Size(max = 160) val name: String,
    @get:Schema(description = "Null for a top-level category")
    val parentId: UUID? = null,
)

@Schema(name = "RenameCategoryRequest")
data class RenameCategoryRequest(@field:NotBlank @field:Size(max = 160) val name: String)

@Schema(name = "CategoryResponse")
data class CategoryResponse(
    val id: UUID,
    val name: String,
    val slug: String,
    @get:Schema(description = "Null for a top-level category")
    val parentId: UUID?,
) {
    companion object {
        fun of(c: Category) = CategoryResponse(requireNotNull(c.id), c.name, c.slug, c.parentId)
    }
}

/**
 * Browsing the taxonomy.
 *
 * Public, like course discovery: a visitor deciding whether to sign up needs to
 * see how the catalogue is organised. Both lists are small and change rarely,
 * so they are returned whole rather than paginated - a client filtering a
 * dropdown should not have to page through it.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Taxonomy", description = "Categories and tags")
class TaxonomyController(private val taxonomyService: TaxonomyService) {

    @GetMapping("/categories")
    @Operation(
        summary = "List all categories",
        description = "Flat, with `parentId` carrying the tree. Curated reference " +
            "data - there is no endpoint to create one.",
    )
    fun categories(): List<CategoryResponse> =
        taxonomyService.listCategories().map(CategoryResponse::of)

    @PostMapping("/admin/categories")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Add a category",
        description = "Requires `category.manage`. Seeding still supplies the base tree; " +
            "this is for the additions a running platform accumulates, which should " +
            "not each need a migration.",
    )
    fun createCategory(
        @Valid @RequestBody request: CreateCategoryRequest,
    ): ResponseEntity<CategoryResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        CategoryResponse.of(taxonomyService.createCategory(request.name, request.parentId)),
    )

    @PatchMapping("/admin/categories/{categoryId}")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Rename a category",
        description = "Requires `category.manage`. The slug is left alone: it is what " +
            "the seeder matches on and what a filter URL carries, so regenerating it " +
            "would orphan the seed entry and break links already shared.",
    )
    fun renameCategory(
        @PathVariable categoryId: UUID,
        @Valid @RequestBody request: RenameCategoryRequest,
    ): CategoryResponse = CategoryResponse.of(taxonomyService.renameCategory(categoryId, request.name))

    @DeleteMapping("/admin/categories/{categoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    @Operation(
        summary = "Delete a category",
        description = "Requires `category.manage`. Refused while it has subcategories or " +
            "any course is in it - both foreign keys cascade, so a delete the database " +
            "allows would silently strip categorisation from every course carrying it.",
    )
    fun deleteCategory(@PathVariable categoryId: UUID) = taxonomyService.deleteCategory(categoryId)

    @GetMapping("/tags")
    @Operation(summary = "List all tags in use", description = "For autocomplete when tagging a course.")
    fun tags(): List<TermResponse> = taxonomyService.listTags().map {
        TermResponse(requireNotNull(it.id), it.name, it.slug)
    }
}
