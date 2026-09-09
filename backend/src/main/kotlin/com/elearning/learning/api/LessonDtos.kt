package com.elearning.learning.api

import com.elearning.learning.application.LessonView
import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.ResourceContentType
import com.elearning.learning.domain.ResourceType
import com.elearning.learning.domain.SourceType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(
    name = "SaveLessonRequest",
    description = """
        What the lesson teaches with is described by two independent fields.
        `resourceType` is what the material is; `sourceType` is where it lives,
        and decides which content field is required: FILE needs `mediaId` -
        except when replacing nothing, where the file already attached is kept -
        URL needs `url`, INLINE needs `content`.
    """,
)
data class SaveLessonRequest(

    @field:NotBlank
    @field:Size(max = 255)
    @get:Schema(description = "Names the material in the library; usually the item's own title")
    val title: String,

    val resourceType: ResourceType,

    val sourceType: SourceType,

    @field:Size(max = 5000)
    val description: String? = null,

    @field:Min(0)
    @get:Schema(description = "How long the author says it takes, not how long the file runs")
    val durationSeconds: Int? = null,

    val completionRule: CompletionRule? = null,

    @get:Schema(description = "The body, for an INLINE lesson")
    val content: String? = null,

    @get:Schema(description = "How to read `content`; defaults to MARKDOWN")
    val contentFormat: ResourceContentType? = null,

    @get:Schema(description = "Where it lives, for a URL lesson. http and https only")
    val url: String? = null,

    @get:Schema(description = "A completed media upload, for a FILE lesson")
    val mediaId: UUID? = null,
)

@Schema(name = "LessonResponse")
data class LessonResponse(
    val courseItemId: UUID,
    val title: String,
    val resourceType: String,
    val sourceType: String,
    val description: String?,
    val durationSeconds: Int?,
    val completionRule: String,
    @get:Schema(description = "Body of an INLINE lesson")
    val content: String?,
    val contentFormat: String?,
    @get:Schema(description = "Target of a URL lesson")
    val url: String?,
    @get:Schema(description = "Whether a file is attached; fetch it from /content-url")
    val hasFile: Boolean,
) {
    companion object {
        fun of(v: LessonView) = LessonResponse(
            courseItemId = v.courseItemId,
            title = v.title,
            resourceType = v.resourceType.name,
            sourceType = v.sourceType.name,
            description = v.description,
            durationSeconds = v.durationSeconds,
            completionRule = v.completionRule.name,
            content = v.content,
            contentFormat = v.contentFormat?.name,
            url = v.url,
            hasFile = v.hasFile,
        )
    }
}

@Schema(name = "LessonContentUrlResponse")
data class LessonContentUrlResponse(
    val contentUrl: String,
    val expiresInSeconds: Long,
)
