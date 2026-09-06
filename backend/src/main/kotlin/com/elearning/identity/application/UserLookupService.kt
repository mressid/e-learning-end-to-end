package com.elearning.identity.application

import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Display details for people, offered by the module that owns them.
 *
 * Every admin listing renders a person - a certificate's holder, a submission's
 * author, a course's owner - and each is in a different module. Identity
 * publishes one implementation rather than each consumer growing its own copy:
 * identity depends on nothing, so depending on it inverts no arrow (§8), and
 * three private duplicates of the same two-table join is worse than one shared
 * read model.
 *
 * Deliberately a *read* model. Nothing here can change an account, so exposing
 * it broadly grants nothing beyond rendering a name.
 */
@Service
class UserLookupService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
) {

    /**
     * A batch, always. Listings render a page at a time, and a call per row is
     * the N+1 this codebase avoids everywhere it renders a list.
     */
    @Transactional(readOnly = true)
    fun summaries(userIds: Collection<UUID>): Map<UUID, PersonSummary> {
        val ids = userIds.filterNotNull().distinct()
        if (ids.isEmpty()) return emptyMap()

        val names = profiles.findAllById(ids).associate { requireNotNull(it.userId) to it.displayName }
        return users.findAllById(ids).associate { user ->
            val id = requireNotNull(user.id)
            id to PersonSummary(id, user.email, user.username, names[id])
        }
    }
}

data class PersonSummary(
    val id: UUID,
    val email: String,
    val username: String,
    val displayName: String?,
) {
    /** What a dashboard row shows: a real name if there is one, else the handle. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: username
}
