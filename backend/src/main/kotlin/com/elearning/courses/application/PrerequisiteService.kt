package com.elearning.courses.application

import com.elearning.courses.domain.CoursePrerequisiteNote
import com.elearning.courses.domain.ItemPrerequisite
import com.elearning.courses.domain.ItemPrerequisiteId
import com.elearning.courses.infrastructure.CourseItemRepository
import com.elearning.courses.infrastructure.CoursePrerequisiteNoteRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.courses.infrastructure.ItemPrerequisiteRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** One stated prerequisite of a course, as the API renders it. */
data class PrerequisiteNoteView(val text: String)

/** An item prerequisite as the API renders it. */
data class PrerequisiteView(val id: UUID, val title: String)

/**
 * What has to be done before something else, at two levels that are
 * deliberately different in kind.
 *
 * **Between courses it is free text** - "Basic Python", "comfortable with
 * matrices" - and nothing enforces it. It used to be a foreign key with an
 * enrolment gate, and that shape had no safe failure mode: once the required
 * course was unpublished, the gated one silently stopped accepting students,
 * and neither repair was acceptable (refusing the unpublish holds one author
 * hostage to another's dependency; dropping the requirement rewrites a course's
 * pedagogy behind its author's back). Nothing even required the two courses to
 * share an owner. A sentence addressed to a prospective student has none of
 * those failure modes.
 *
 * **Between items of one course it is still enforced**, because there the
 * hazard does not exist: both items sit in the same course under the same
 * owner, so no second author's publish decision can break the requirement, and
 * "watch the video before the quiz" is a guarantee worth keeping.
 */
@Service
class PrerequisiteService(
    private val courses: CourseRepository,
    private val items: CourseItemRepository,
    private val notes: CoursePrerequisiteNoteRepository,
    private val itemPrerequisites: ItemPrerequisiteRepository,
    private val authorization: CourseAuthorization,
) {

    // ---- courses ---------------------------------------------------------

    @Transactional(readOnly = true)
    fun listCoursePrerequisites(courseId: UUID, viewerId: UUID?): List<PrerequisiteNoteView> {
        // Answering for a draft would confirm it exists, which is the whole
        // point of drafts returning 404 rather than 403 elsewhere.
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanView(course, viewerId)

        return notes.findByCourseIdOrderByPosition(courseId).map { PrerequisiteNoteView(it.text) }
    }

    @Transactional
    fun setCoursePrerequisites(
        courseId: UUID,
        texts: List<String>,
        editorId: UUID,
    ): List<PrerequisiteNoteView> {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        // Blank entries would render as empty bullets, and duplicates as the
        // same line twice; both are typos rather than intent.
        val cleaned = texts.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }

        if (cleaned.size > MAX_NOTES) {
            throw BusinessRuleException(
                "TOO_MANY_PREREQUISITES",
                "A course may state at most $MAX_NOTES prerequisites",
            )
        }

        notes.deleteByCourseId(courseId)
        // Delete and insert both touch the unique (course_id, position), so the
        // removal has to reach the database before the rows go back in.
        notes.flush()
        notes.saveAll(
            cleaned.mapIndexed { index, text -> CoursePrerequisiteNote(courseId, index, text) },
        )
        return cleaned.map(::PrerequisiteNoteView)
    }

    // ---- items -----------------------------------------------------------

    @Transactional(readOnly = true)
    fun listItemPrerequisites(itemId: UUID, viewerId: UUID?): List<PrerequisiteView> {
        val courseId = items.findCourseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        // Item titles of an unpublished course are not public; the section
        // listing already guards them the same way.
        authorization.requireCanView(course, viewerId)

        val ids = itemPrerequisites.prerequisiteIdsOf(itemId)
        if (ids.isEmpty()) return emptyList()
        return items.findAllById(ids)
            .map { PrerequisiteView(requireNotNull(it.id), it.title) }
            .sortedBy(PrerequisiteView::title)
    }

    @Transactional
    fun setItemPrerequisites(
        itemId: UUID,
        prerequisiteIds: List<UUID>,
        editorId: UUID,
    ): List<PrerequisiteView> {
        val courseId = items.findCourseIdOfItem(itemId)
            ?: throw NotFoundException("COURSE_ITEM_NOT_FOUND", "Course item not found")
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)

        val wanted = prerequisiteIds.distinct()
        if (itemId in wanted) {
            throw BusinessRuleException("SELF_PREREQUISITE", "An item cannot require itself")
        }

        val found = items.findAllById(wanted).associateBy { requireNotNull(it.id) }
        val missing = wanted.filterNot(found::containsKey)
        if (missing.isNotEmpty()) {
            throw NotFoundException("COURSE_ITEM_NOT_FOUND", "No item with id ${missing.first()}")
        }
        // Gating an item behind one from another course would lock it forever:
        // the student would have to enrol elsewhere to unlock this course, and
        // nothing in the model says they may.
        val foreign = wanted.filter { items.findCourseIdOfItem(it) != courseId }
        if (foreign.isNotEmpty()) {
            throw BusinessRuleException(
                "PREREQUISITE_OUTSIDE_COURSE",
                "An item's prerequisites must belong to the same course",
            )
        }

        detectCycle(itemId, wanted) { itemPrerequisites.prerequisiteIdsOfAll(it) }

        itemPrerequisites.deleteByIdItemId(itemId)
        itemPrerequisites.flush()
        itemPrerequisites.saveAll(wanted.map { ItemPrerequisite(ItemPrerequisiteId(itemId, it)) })

        return wanted.map { found.getValue(it) }
            .map { PrerequisiteView(requireNotNull(it.id), it.title) }
            .sortedBy(PrerequisiteView::title)
    }

    // ---- the cycle walk --------------------------------------------------

    /**
     * Refuses [candidates] as prerequisites of [target] if [target] is already
     * reachable from any of them.
     *
     * Walks the existing edges breadth-first from the candidates: if the walk
     * arrives back at [target], then adding these edges would close a loop.
     * `visited` makes it terminate even though the stored graph may already
     * contain a cycle put there by an earlier version of this code or by hand -
     * a guard that assumed a clean graph would hang on a dirty one.
     */
    private fun detectCycle(
        target: UUID,
        candidates: List<UUID>,
        prerequisitesOf: (Collection<UUID>) -> List<UUID>,
    ) {
        val visited = candidates.toMutableSet()
        var frontier: Collection<UUID> = candidates
        while (frontier.isNotEmpty()) {
            val next = prerequisitesOf(frontier).toSet()
            if (target in next) {
                throw BusinessRuleException(
                    "PREREQUISITE_CYCLE",
                    "That would create a prerequisite loop, leaving both unreachable",
                )
            }
            frontier = next - visited
            visited += frontier
        }
    }

    private companion object {
        /** A prospective student reads these; a list of forty is not a list. */
        const val MAX_NOTES = 20
    }
}
