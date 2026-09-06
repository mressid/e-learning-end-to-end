package com.elearning.courses.infrastructure

import com.elearning.platform.media.MediaReferenceProbe
import org.springframework.stereotype.Component
import java.util.UUID

/** Courses' answer: the file is a course thumbnail. */
@Component
class CourseMediaReferenceProbe(private val courses: CourseRepository) : MediaReferenceProbe {
    override fun isReferenced(mediaId: UUID): Boolean = courses.existsByThumbnailMediaId(mediaId)
    override fun describe(): String = "a course thumbnail"
}
