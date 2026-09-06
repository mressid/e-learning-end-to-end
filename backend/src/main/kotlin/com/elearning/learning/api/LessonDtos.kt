package com.elearning.learning.api

import com.elearning.learning.application.LessonView
import com.elearning.learning.domain.CompletionRule
import com.elearning.learning.domain.LessonContentType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import java.util.UUID

@Schema(
    name = "SaveLessonRequest",
    description = """
        Content fields depend on contentType: ARTICLE needs `content`,
        VIDEO and DOCUMENT need `mediaId` of a completed upload.
    """,
)
data class SaveLessonRequest(
    val contentType: LessonContentType,

    @field:Size(max = 5000)
    val description: String? = null,

    @field:Min(0)
    val durationSeconds: Int? = null,

    val completionRule: CompletionRule? = null,

    @get:Schema(description = "Markdown or HTML body, for ARTICLE lessons")
    val content: String? = null,

    @get:Schema(description = "A completed media upload, for VIDEO and DOCUMENT lessons")
    val mediaId: UUID? = null,

    val thumbnailMediaId: UUID? = null,
)

@Schema(name = "LessonResponse")
data class LessonResponse(
    val courseItemId: UUID,
    val contentType: String,
    val description: String?,
    val durationSeconds: Int?,
    val completionRule: String,
    @get:Schema(description = "Body of an ARTICLE lesson")
    val content: String?,
    @get:Schema(description = "Whether a file is attached; fetch it from /content-url")
    val hasFile: Boolean,
) {
    companion object {
        fun of(v: LessonView) = LessonResponse(
            courseItemId = v.courseItemId,
            contentType = v.contentType.name,
            description = v.description,
            durationSeconds = v.durationSeconds,
            completionRule = v.completionRule.name,
            content = v.article,
            hasFile = v.hasFile,
        )
    }
}

@Schema(name = "LessonContentUrlResponse")
data class LessonContentUrlResponse(
    val contentUrl: String,
    val expiresInSeconds: Long,
)
