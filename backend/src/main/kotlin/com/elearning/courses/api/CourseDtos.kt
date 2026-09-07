package com.elearning.courses.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.elearning.courses.application.TermView
import com.elearning.courses.domain.Course
import com.elearning.courses.domain.CourseItem
import com.elearning.courses.domain.CourseItemType
import com.elearning.courses.domain.CourseLevel
import com.elearning.courses.domain.CourseSection
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

@Schema(name = "CreateCourseRequest")
data class CreateCourseRequest(
    @field:NotBlank @field:Size(max = 255)
    @get:Schema(example = "Advanced Python")
    val title: String,

    @field:Size(max = 500) val shortDescription: String? = null,
    val description: String? = null,
    val level: CourseLevel? = null,
    @field:Size(max = 16) val language: String? = null,
    @get:Schema(
        description = "Days of access a student gets on enrolling. Omit or send null " +
            "for access that never lapses. Applies to enrolments made after the " +
            "change; nobody already enrolled loses time they were given.",
        example = "365",
    )
    @field:Min(1)
    val accessDurationDays: Int? = null,
)

@Schema(name = "UpdateCourseRequest", description = "Only the fields present are changed")
data class UpdateCourseRequest(
    @field:Size(max = 255) val title: String? = null,
    @field:Size(max = 500) val shortDescription: String? = null,
    val description: String? = null,
    val level: CourseLevel? = null,
    @field:Size(max = 16) val language: String? = null,
    @get:Schema(
        description = "Days of access a student gets on enrolling. Omit or send null " +
            "for access that never lapses. Applies to enrolments made after the " +
            "change; nobody already enrolled loses time they were given.",
        example = "365",
    )
    @field:Min(1)
    val accessDurationDays: Int? = null,
)

@Schema(name = "SetThumbnailRequest")
data class SetThumbnailRequest(val mediaId: UUID)

@Schema(name = "CourseResponse")
data class CourseResponse(
    val id: UUID,
    val title: String,
    val slug: String,
    val shortDescription: String?,
    val description: String?,
    val status: String,
    val level: String,
    val language: String,
    val ownerId: UUID,
    val createdAt: Instant,
    val publishedAt: Instant?,
    @get:Schema(description = "Stable public URL; null when no thumbnail is set")
    val thumbnailUrl: String? = null,
    val categories: List<TermResponse> = emptyList(),
    val tags: List<TermResponse> = emptyList(),
    @get:Schema(description = "Days of access granted at enrolment; absent means it never lapses")
    val accessDurationDays: Int? = null,
) {
    companion object {
        fun of(
            course: Course,
            thumbnailUrl: String? = null,
            categories: List<TermView> = emptyList(),
            tags: List<TermView> = emptyList(),
        ) = CourseResponse(
            id = requireNotNull(course.id),
            title = course.title,
            slug = course.slug,
            shortDescription = course.shortDescription,
            description = course.description,
            status = course.status.name,
            level = course.level.name,
            language = course.language,
            ownerId = course.ownerId,
            createdAt = course.createdAt,
            publishedAt = course.publishedAt,
            thumbnailUrl = thumbnailUrl,
            categories = categories.map(TermResponse::of),
            tags = tags.map(TermResponse::of),
            accessDurationDays = course.accessDurationDays,
        )
    }
}

@Schema(name = "Term", description = "A category or a tag")
data class TermResponse(val id: UUID, val name: String, val slug: String) {
    companion object {
        fun of(term: TermView) = TermResponse(term.id, term.name, term.slug)
    }
}

@Schema(name = "CreateSectionRequest")
data class CreateSectionRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val description: String? = null,
)

@Schema(name = "SectionResponse")
data class SectionResponse(
    val id: UUID,
    val title: String,
    val description: String?,
    val position: Int,
) {
    companion object {
        fun of(section: CourseSection) = SectionResponse(
            id = requireNotNull(section.id),
            title = section.title,
            description = section.description,
            position = section.position,
        )
    }
}

@Schema(name = "CreateCourseItemRequest")
data class CreateCourseItemRequest(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val type: CourseItemType,
    @get:JsonProperty("isRequired") val isRequired: Boolean = true,
)

@Schema(name = "CourseItemResponse")
data class CourseItemResponse(
    val id: UUID,
    val title: String,
    val type: String,
    val position: Int,
    @get:JsonProperty("isRequired") val isRequired: Boolean,
) {
    companion object {
        fun of(item: CourseItem) = CourseItemResponse(
            id = requireNotNull(item.id),
            title = item.title,
            type = item.type.name,
            position = item.position,
            isRequired = item.isRequired,
        )
    }
}

@Schema(name = "SetCategoriesRequest")
data class SetCategoriesRequest(
    @get:Schema(description = "Replaces the current set; an empty list clears it")
    @field:Size(max = 10)
    val categoryIds: List<UUID> = emptyList(),
)

@Schema(name = "SetTagsRequest")
data class SetTagsRequest(
    @get:Schema(description = "Free text; matched and created by slug", example = "[\"Machine Learning\"]")
    @field:Size(max = 25)
    val tags: List<@NotBlank @Size(max = 80) String> = emptyList(),
)
