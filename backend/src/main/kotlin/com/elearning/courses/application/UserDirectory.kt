package com.elearning.courses.application

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

/**
 * What courses needs to know about a person.
 *
 * Two things: whether they exist, and enough to render them in a roster.
 * Declared here by the consumer, so courses never touches identity's entities
 * and the dependency stays one-directional (§8).
 */
interface UserDirectory {

    fun exists(userId: UUID): Boolean

    /**
     * Display details for a set of people, in one lookup.
     *
     * A batch rather than one call per row: the instructor roster renders a
     * page at a time, and per-row resolution is the N+1 the course listing
     * already avoids for thumbnails.
     */
    fun summaries(userIds: Collection<UUID>): Map<UUID, UserSummary>

    /**
     * The instructor accounts.
     *
     * Asked of identity rather than derived here from `courses.owner_id`: an
     * instructor appointed this morning has no courses yet, and is exactly the
     * person an administrator has just created and wants to see.
     *
     * Still consumer-declared, so the dependency stays one-directional (§8):
     * courses asks identity, never the reverse.
     */
    fun instructors(term: String?, pageable: Pageable): Page<UserSummary>

    /**
     * Whether this account may author courses.
     *
     * There used to be the opposite of this - a `markAsInstructor` that flipped
     * a flag on whoever created a course, so that authoring made you an author.
     * The two kinds of account are now distinct and permanent, so the question
     * is asked before the fact instead of asserted after it. The database agrees
     * independently: `courses.owner_id` references the instructor table.
     */
    fun isInstructor(userId: UUID): Boolean
}

data class UserSummary(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String?,
)
