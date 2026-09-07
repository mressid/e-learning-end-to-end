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
     * The people flagged as instructors.
     *
     * Asked of identity rather than derived here from `courses.owner_id`, because
     * since V11 the flag is the fact and ownership is a consequence of it. The
     * old derivation could not show an instructor who had not started a course
     * yet, which is exactly the person an administrator has just enrolled.
     *
     * Still consumer-declared, so the dependency stays one-directional (§8):
     * courses asks identity, never the reverse.
     */
    fun instructors(term: String?, pageable: Pageable): Page<UserSummary>

    /**
     * Records that someone authors courses, because they just started one.
     *
     * Without this the flag and reality drift apart: anyone may still create a
     * course, so a self-service author would own courses and yet be missing from
     * the roster of people who author courses. Flagging on create keeps the two
     * in agreement and changes nothing about who is *allowed* to author - that
     * remains open, and gating it is a separate decision.
     *
     * Idempotent, and never clears the flag: losing your last course does not
     * un-appoint you.
     */
    fun markAsInstructor(userId: UUID)
}

data class UserSummary(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String?,
)
