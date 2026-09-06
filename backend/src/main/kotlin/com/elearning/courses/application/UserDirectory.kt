package com.elearning.courses.application

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
}

data class UserSummary(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String?,
)
