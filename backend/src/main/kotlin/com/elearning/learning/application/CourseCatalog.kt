package com.elearning.learning.application

import java.util.UUID

/**
 * What the learning module needs to know about courses - and nothing more.
 *
 * Declared here, by the consumer, so learning never touches `Course` internals
 * and can be tested without the courses module. The implementation lives in
 * `learning.infrastructure`, which keeps the dependency one-directional:
 * learning depends on courses, courses knows nothing about learning (§8).
 */
interface CourseCatalog {

    fun exists(courseId: UUID): Boolean

    fun isPublished(courseId: UUID): Boolean

    /** The course an item belongs to, or null if there is no such item. */
    fun courseIdOfItem(itemId: UUID): UUID?

    /** Items that must be completed for the course to count as finished. */
    fun requiredItemIds(courseId: UUID): List<UUID>

    /**
     * Whether [userId] may author this course. Asked of the courses module
     * rather than re-derived here: ownership and co-instructor rules belong to
     * whoever owns courses.
     */
    fun canEdit(courseId: UUID, userId: UUID): Boolean

    /** The item's type name (LESSON, QUIZ, ASSIGNMENT), or null if absent. */
    fun itemType(itemId: UUID): String?

    /** Course title, for anything that has to render or announce it. */
    fun courseTitle(courseId: UUID): String?

    /** Item title, for a listing that names the piece of work rather than its id. */
    fun itemTitle(itemId: UUID): String?

    /**
     * Days of access this course grants, or null if it never lapses.
     *
     * Learning asks once, when the student enrols, and stores the resulting
     * date on the enrolment. Courses owns how long the deal lasts; learning
     * owns when a particular student's copy of it runs out.
     */
    fun accessDurationDays(courseId: UUID): Int?

    /**
     * Items of the same course that must be completed before this one.
     *
     * There is deliberately no course-level equivalent: prerequisites *between*
     * courses are free text an author writes for a prospective student, not a
     * gate, so learning has nothing to enforce and nothing to ask about.
     */
    fun prerequisiteItemIds(itemId: UUID): List<UUID>
}
