package com.elearning.courses.infrastructure

import com.elearning.courses.application.UserDirectory
import com.elearning.courses.application.UserSummary
import com.elearning.identity.application.UserLookupService
import com.elearning.identity.infrastructure.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The one place courses reaches into identity.
 *
 * The port stays consumer-declared - courses says what it needs - but the join
 * behind it is identity's `UserLookupService`, so the same two-table lookup is
 * not reimplemented per module.
 */
@Component
class UserDirectoryAdapter(
    private val users: UserRepository,
    private val lookup: UserLookupService,
) : UserDirectory {

    override fun exists(userId: UUID): Boolean = users.existsById(userId)

    override fun summaries(userIds: Collection<UUID>): Map<UUID, UserSummary> =
        lookup.summaries(userIds).mapValues { (_, p) ->
            UserSummary(p.id, p.email, p.username, p.displayName)
        }

    override fun markAsInstructor(userId: UUID) {
        val user = users.findById(userId).orElse(null) ?: return
        if (!user.isInstructor) {
            user.isInstructor = true
            users.save(user)
        }
    }

    override fun instructors(term: String?, pageable: Pageable): Page<UserSummary> {
        val page = if (term.isNullOrBlank()) {
            users.findByIsInstructorTrue(pageable)
        } else {
            users.searchInstructors(term.trim(), pageable)
        }
        // One profile lookup for the page, not one per row.
        val profiles = lookup.summaries(page.content.mapNotNull { it.id })
        return page.map { user ->
            val id = requireNotNull(user.id)
            UserSummary(id, user.email, user.username, profiles[id]?.displayName)
        }
    }
}
