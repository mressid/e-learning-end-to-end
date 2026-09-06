package com.elearning.courses.infrastructure

import com.elearning.courses.application.UserDirectory
import com.elearning.courses.application.UserSummary
import com.elearning.identity.application.UserLookupService
import com.elearning.identity.infrastructure.UserRepository
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
}
