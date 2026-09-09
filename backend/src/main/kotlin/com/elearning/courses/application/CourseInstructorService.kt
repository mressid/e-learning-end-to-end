package com.elearning.courses.application

import com.elearning.courses.domain.CourseInstructor
import com.elearning.courses.domain.CourseInstructorId
import com.elearning.courses.domain.InstructorRole
import com.elearning.courses.infrastructure.CourseInstructorRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ForbiddenException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Co-instructors on a course.
 *
 * `CourseAuthorization` has always granted edit rights to anyone in
 * `course_instructors`, but nothing could put a row there — the capability was
 * unreachable. This closes that.
 *
 * Managing the roster is **owner-only**, deliberately: if a co-instructor could
 * add co-instructors, one assistant could quietly hand out authority over
 * somebody else's course.
 */
@Service
class CourseInstructorService(
    private val courses: CourseRepository,
    private val instructors: CourseInstructorRepository,
    private val users: UserDirectory,
    private val authorization: CourseAuthorization,
) {

    @Transactional
    fun add(courseId: UUID, instructorId: UUID, role: InstructorRole, actorId: UUID): CourseInstructor {
        val course = requireOwnedCourse(courseId, actorId)

        if (!users.exists(instructorId)) {
            throw NotFoundException("USER_NOT_FOUND", "No such user")
        }
        // A student account cannot co-instruct, for the same reason it cannot
        // own: the two kinds are separate and permanent. Someone who should be
        // teaching needs an instructor account, not an exception here.
        if (!users.isInstructor(instructorId)) {
            throw BusinessRuleException(
                "NOT_AN_INSTRUCTOR",
                "That account is a student. Only an instructor account can be added to a course.",
            )
        }
        // The owner's authority comes from `courses.owner_id`; duplicating it
        // here would create two sources of truth for the same person.
        if (course.ownerId == instructorId) {
            throw BusinessRuleException("OWNER_IS_NOT_AN_INSTRUCTOR", "The owner already has full access")
        }

        val id = CourseInstructorId(courseId, instructorId)
        return instructors.findById(id).orElseGet {
            instructors.save(CourseInstructor(id, role))
        }.also { it.role = role }
    }

    @Transactional
    fun remove(courseId: UUID, instructorId: UUID, actorId: UUID) {
        requireOwnedCourse(courseId, actorId)
        instructors.deleteById(CourseInstructorId(courseId, instructorId))
    }

    /** Visible to anyone who can edit the course, not just the owner. */
    @Transactional(readOnly = true)
    fun list(courseId: UUID, actorId: UUID): List<CourseInstructor> {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, actorId)
        return instructors.findByIdCourseId(courseId)
    }

    private fun requireOwnedCourse(courseId: UUID, actorId: UUID) =
        courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
            .also {
                if (it.ownerId != actorId) {
                    throw ForbiddenException(
                        "COURSE_OWNER_ONLY",
                        "Only the course owner can manage instructors",
                    )
                }
            }
}
