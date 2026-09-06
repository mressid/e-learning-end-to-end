package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

/**
 * Content tables, each keyed by the lesson they belong to.
 *
 * Kept separate rather than as one nullable-heavy table so each content type can
 * gain its own fields (renditions, subtitles, page counts) without widening a
 * shared row.
 */
@Entity
@Table(name = "video_contents")
class VideoContent(

    @Id
    @Column(name = "lesson_id", nullable = false, updatable = false)
    val lessonId: UUID,

    @Column(name = "media_id")
    var mediaId: UUID? = null,

    @Column(name = "thumbnail_media_id")
    var thumbnailMediaId: UUID? = null,

    /** Filled by the transcoding pipeline; nothing writes it yet. */
    @Column(name = "hls_manifest_media_id")
    var hlsManifestMediaId: UUID? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Int? = null,
)

@Entity
@Table(name = "article_contents")
class ArticleContent(

    @Id
    @Column(name = "lesson_id", nullable = false, updatable = false)
    val lessonId: UUID,

    @Column(nullable = false)
    var content: String,
)

@Entity
@Table(name = "document_contents")
class DocumentContent(

    @Id
    @Column(name = "lesson_id", nullable = false, updatable = false)
    val lessonId: UUID,

    @Column(name = "media_id")
    var mediaId: UUID? = null,
)
