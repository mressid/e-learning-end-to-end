package com.elearning.courses.application

import com.elearning.courses.domain.CourseItem
import com.elearning.courses.domain.CourseItemType
import com.elearning.courses.domain.CourseSection
import com.elearning.courses.infrastructure.CourseItemRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.courses.infrastructure.CourseSectionRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * The `Course -> Section -> CourseItem` hierarchy.
 *
 * Every operation is authorized against the owning *course*, not the section or
 * item, because that is where authority actually lives.
 */
@Service
class CourseStructureService(
    private val courses: CourseRepository,
    private val sections: CourseSectionRepository,
    private val items: CourseItemRepository,
    private val authorization: CourseAuthorization,
    /**
     * Every module that holds student data registers one. Courses asks the
     * question and depends on none of them (§8), so a new kind of student
     * record means a new probe rather than an edit to the delete path.
     */
    private val activityProbes: List<ItemActivityProbe>,
) {

    @Transactional
    fun addSection(courseId: UUID, title: String, description: String?, editorId: UUID): CourseSection {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        return sections.save(
            CourseSection(
                courseId = courseId,
                title = title,
                description = description,
                // Append; explicit reordering is a separate operation.
                position = sections.maxPosition(courseId) + 1,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun listSections(courseId: UUID, viewerId: UUID?): List<CourseSection> {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanView(course, viewerId)
        return sections.findByCourseIdOrderByPosition(courseId)
    }

    @Transactional
    fun addItem(sectionId: UUID, title: String, type: CourseItemType, isRequired: Boolean, editorId: UUID): CourseItem {
        val section = sections.findById(sectionId)
            .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        val course = courses.findById(section.courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        return items.save(
            CourseItem(
                sectionId = sectionId,
                title = title,
                type = type,
                isRequired = isRequired,
                position = items.maxPosition(sectionId) + 1,
            ),
        )
    }

    /**
     * Reorders a course's sections.
     *
     * The request must list every section exactly once: a partial order would
     * leave the rest at stale positions and silently interleave them. The
     * `(course_id, position)` unique constraint is DEFERRABLE INITIALLY DEFERRED,
     * so positions can be rewritten inside one transaction without tripping over
     * an intermediate collision.
     */
    @Transactional
    fun reorderSections(courseId: UUID, orderedIds: List<UUID>, editorId: UUID): List<CourseSection> {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        val current = sections.findByCourseIdOrderByPosition(courseId)
        requireExactCover(current.mapNotNull { it.id }, orderedIds, "sections")

        val byId = current.associateBy { requireNotNull(it.id) }
        orderedIds.forEachIndexed { index, id -> requireNotNull(byId[id]).position = index }
        return orderedIds.map { requireNotNull(byId[it]) }
    }

    @Transactional
    fun reorderItems(sectionId: UUID, orderedIds: List<UUID>, editorId: UUID): List<CourseItem> {
        val section = sections.findById(sectionId)
            .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        val course = courses.findById(section.courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        val current = items.findBySectionIdOrderByPosition(sectionId)
        requireExactCover(current.mapNotNull { it.id }, orderedIds, "items")

        val byId = current.associateBy { requireNotNull(it.id) }
        orderedIds.forEachIndexed { index, id -> requireNotNull(byId[id]).position = index }
        return orderedIds.map { requireNotNull(byId[it]) }
    }

    /** The new order must be a permutation of what is there: no gaps, no strangers. */
    private fun requireExactCover(existing: List<UUID>, requested: List<UUID>, what: String) {
        if (requested.size != requested.toSet().size) {
            throw BusinessRuleException("DUPLICATE_IN_ORDER", "The order lists the same $what entry twice")
        }
        if (existing.toSet() != requested.toSet()) {
            throw BusinessRuleException(
                "INCOMPLETE_ORDER",
                "The order must list every one of the $what exactly once",
            )
        }
    }

    /**
     * Removes one item, and everything the schema hangs off it.
     *
     * The database cascades from `course_items` into `lessons`, `quizzes` and
     * `assignments` - and onward into quiz attempts, quiz responses, assignment
     * submissions and `learning_progress`. So deleting an item people have
     * worked through does not tidy up a course, it destroys graded student work
     * with no way back.
     *
     * Authoring material is the editor's to remove; a student's answers and
     * marks are not. The delete is therefore refused outright once anyone has
     * touched the item, naming what is in the way. None of it is recoverable
     * afterwards, which is why the check is up front rather than a warning
     * attached to the response.
     */
    @Transactional
    fun deleteItem(itemId: UUID, editorId: UUID) {
        val item = items.findById(itemId)
            .orElseThrow { NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found") }
        requireEditorOfSection(item.sectionId, editorId, "course.delete")
        requireNoStudentActivity(listOf(itemId))
        items.delete(item)
    }

    /**
     * Removes a section and every item under it.
     *
     * All or nothing: if one item in the section has been worked on, the whole
     * delete is refused rather than leaving a half-emptied section whose
     * survivors are whichever items happened to have submissions.
     */
    @Transactional
    fun deleteSection(sectionId: UUID, editorId: UUID) {
        val section = sections.findById(sectionId)
            .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        val course = courses.findById(section.courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId, "course.delete")

        val itemIds = items.findBySectionIdOrderByPosition(sectionId).mapNotNull { it.id }
        requireNoStudentActivity(itemIds)
        // course_items references course_sections ON DELETE CASCADE, so the
        // items go with it; they are loaded above only to be checked.
        sections.delete(section)
    }

    private fun requireEditorOfSection(sectionId: UUID, editorId: UUID, orPermission: String? = null) {
        val section = sections.findById(sectionId)
            .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        val course = courses.findById(section.courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        if (orPermission == null) {
            authorization.requireCanEdit(course, editorId)
        } else {
            authorization.requireCanEdit(course, editorId, orPermission)
        }
    }

    private fun requireNoStudentActivity(itemIds: List<UUID>) {
        if (itemIds.isEmpty()) return
        val blocking = activityProbes.filter { it.hasStudentActivity(itemIds) }
        if (blocking.isNotEmpty()) {
            throw BusinessRuleException(
                "ITEM_HAS_STUDENT_ACTIVITY",
                "Cannot delete: this would destroy " +
                    blocking.joinToString(" and ") { it.describe() } +
                    ". Archive the course instead.",
            )
        }
    }

    @Transactional(readOnly = true)
    fun listItems(sectionId: UUID, viewerId: UUID?): List<CourseItem> {
        val section = sections.findById(sectionId)
            .orElseThrow { NotFoundException("SECTION_NOT_FOUND", "Section not found") }
        val course = courses.findById(section.courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanView(course, viewerId)
        return items.findBySectionIdOrderByPosition(sectionId)
    }
}
