package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/** What the resource *is*. Mirrors `resources.resource_type`. */
enum class ResourceType { DOCUMENT, SOURCE_CODE, VIDEO, AUDIO, IMAGE, LINK, OTHER }

/**
 * Where its content lives. Deliberately separate from [ResourceType]: a PDF and
 * a link to a PDF are the same kind of thing held two different ways.
 */
enum class SourceType { FILE, URL, INLINE }

/** Mirrors `resource_contents.content_type`. */
enum class ResourceContentType { MARKDOWN, HTML, PLAIN_TEXT }

/**
 * A reusable learning material, independent of any course.
 *
 * The same cheat sheet can hang off a course, a section and a lesson at once,
 * which is why attachment lives in separate join tables rather than a
 * `course_id` column here.
 */
@Entity
@Table(name = "resources")
class Resource(

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false)
    var resourceType: ResourceType,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, updatable = false)
    val sourceType: SourceType,

    @Column(name = "created_by", updatable = false)
    val createdBy: UUID? = null,

    @Column
    var description: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()

    fun touch(at: Instant = Instant.now()) {
        updatedAt = at
    }
}

@Entity
@Table(name = "resource_files")
class ResourceFile(

    @Id
    @Column(name = "resource_id", nullable = false, updatable = false)
    val resourceId: UUID,

    @Column(name = "media_id", nullable = false)
    var mediaId: UUID,

    @Column
    var filename: String? = null,

    @Column(name = "mime_type")
    var mimeType: String? = null,

    @Column
    var extension: String? = null,

    @Column(name = "size_bytes")
    var sizeBytes: Long? = null,
)

@Entity
@Table(name = "resource_urls")
class ResourceUrl(

    @Id
    @Column(name = "resource_id", nullable = false, updatable = false)
    val resourceId: UUID,

    @Column(nullable = false)
    var url: String,
)

@Entity
@Table(name = "resource_contents")
class ResourceContent(

    @Id
    @Column(name = "resource_id", nullable = false, updatable = false)
    val resourceId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    var contentType: ResourceContentType,

    @Column(nullable = false)
    var content: String,
)
