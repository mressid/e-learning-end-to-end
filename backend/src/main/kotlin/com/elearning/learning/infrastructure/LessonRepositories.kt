package com.elearning.learning.infrastructure

import com.elearning.learning.domain.ArticleContent
import com.elearning.learning.domain.DocumentContent
import com.elearning.learning.domain.Lesson
import com.elearning.learning.domain.VideoContent
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface LessonRepository : JpaRepository<Lesson, UUID>

interface VideoContentRepository : JpaRepository<VideoContent, UUID> {
    fun existsByMediaId(mediaId: UUID): Boolean
    fun existsByThumbnailMediaId(thumbnailMediaId: UUID): Boolean
    fun existsByHlsManifestMediaId(hlsManifestMediaId: UUID): Boolean
}

interface ArticleContentRepository : JpaRepository<ArticleContent, UUID>

interface DocumentContentRepository : JpaRepository<DocumentContent, UUID> {
    fun existsByMediaId(mediaId: UUID): Boolean
}
