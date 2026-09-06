package com.elearning.community.application

import java.util.UUID

/**
 * What community needs from courses and learning, and nothing more (§8).
 */
interface CommunityContext {

    fun courseExists(courseId: UUID): Boolean

    fun isPublished(courseId: UUID): Boolean

    fun canEditCourse(courseId: UUID, userId: UUID): Boolean

    /** Currently studying the course. */
    fun isEnrolled(courseId: UUID, userId: UUID): Boolean

    /** Ever enrolled, whatever the enrolment's state now - the basis for reviewing. */
    fun hasStudied(courseId: UUID, userId: UUID): Boolean

    fun courseIdOfItem(itemId: UUID): UUID?

    /**
     * Whether lesson *content* exists for this item. `discussion_threads.lesson_id`
     * references `lessons`, not `course_items`, so a LESSON item that has no
     * content yet cannot carry a thread.
     */
    fun lessonExists(lessonId: UUID): Boolean
}
