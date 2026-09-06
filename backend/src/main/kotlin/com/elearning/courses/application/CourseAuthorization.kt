package com.elearning.courses.application

import com.elearning.courses.domain.Course
import com.elearning.courses.domain.CourseInstructorId
import com.elearning.courses.infrastructure.CourseInstructorRepository
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.security.PlatformAccess
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Answers "may this user modify THIS course?" (§11).
 *
 * Deliberately not a role check. Holding an instructor role somewhere on the
 * platform says nothing about a particular course, so authority is derived from
 * the relationship to the course itself: its owner, or a co-instructor on it.
 */
@Component
class CourseAuthorization(
    private val instructors: CourseInstructorRepository,
    private val platformAccess: PlatformAccess,
) {

    fun canEdit(course: Course, userId: UUID): Boolean {
        if (course.ownerId == userId) return true
        val courseId = course.id ?: return false
        if (instructors.existsById(CourseInstructorId(courseId, userId))) return true
        // Platform administration, checked *after* the relationship and never
        // instead of it: an administrator has no edge to this course, so there
        // is nothing to derive authority from and a permission is the only
        // honest way to express it. Ordering matters - checking the permission
        // first would make co-instructorship irrelevant whenever an admin
        // permission happened to cover the same action.
        return platformAccess.has("course.write")
    }

    fun requireCanEdit(course: Course, userId: UUID) {
        if (!canEdit(course, userId)) {
            throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
        }
    }

    /**
     * Edit rights, or one narrower platform permission.
     *
     * `course.write` is the whole of authoring, and most admin work does not
     * need it: someone who publishes a queue of finished courses should not
     * thereby be able to rewrite their content, and someone who prunes empty
     * sections should not be able to publish. Passing the specific permission
     * keeps those separable instead of collapsing into one super-permission.
     */
    fun requireCanEdit(course: Course, userId: UUID, orPermission: String) {
        if (canEdit(course, userId) || platformAccess.has(orPermission)) return
        throw ForbiddenException("COURSE_ACCESS_DENIED", "You are not allowed to modify this course")
    }

    /**
     * Drafts are visible only to people who can edit them; published courses to
     * everyone, including anonymous callers.
     */
    fun requireCanView(course: Course, userId: UUID?) {
        if (course.isPublished) return
        // `course.read` is the dashboard's view of the catalogue: seeing a draft
        // without being able to touch it, which no relationship can express.
        if (platformAccess.has("course.read")) return
        if (userId == null || !canEdit(course, userId)) {
            // Same error a missing course produces: whether an unpublished
            // course exists is itself not public information.
            throw com.elearning.shared.errors.NotFoundException("COURSE_NOT_FOUND", "Course not found")
        }
    }
}
