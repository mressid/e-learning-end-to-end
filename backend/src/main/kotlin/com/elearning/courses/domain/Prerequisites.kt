package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/**
 * A stated prerequisite for a course, in the author's own words.
 *
 * Deliberately text and not a link to another course. An enforced course ->
 * course edge has no safe behaviour once the prerequisite is unpublished: the
 * gated course silently closes to new students, and both repairs are worse -
 * refusing the unpublish holds one author hostage to another's dependency,
 * dropping the requirement rewrites a course's pedagogy behind its author's
 * back. A sentence to a prospective student cannot be broken by somebody
 * else's publish decision.
 *
 * Ordering is kept because it reads as a sequence rather than a set.
 */
@Entity
@Table(name = "course_prerequisite_notes")
class CoursePrerequisiteNote(

    @Column(name = "course_id", nullable = false)
    val courseId: UUID,

    @Column(nullable = false)
    var position: Int,

    @Column(nullable = false, length = 500)
    var text: String,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}

/**
 * Ordering *within* one course, and still enforced.
 *
 * Safe where the course-level version was not: both items belong to the same
 * course under the same owner, so there is no second author whose publish
 * decision can break the requirement.
 */
@Entity
@Table(name = "item_prerequisites")
class ItemPrerequisite(@EmbeddedId val id: ItemPrerequisiteId)

@Embeddable
data class ItemPrerequisiteId(
    @Column(name = "item_id") val itemId: UUID,
    @Column(name = "prerequisite_item_id") val prerequisiteItemId: UUID,
) : Serializable
