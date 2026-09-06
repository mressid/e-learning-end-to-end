package com.elearning.learning.application

import java.util.UUID

/**
 * The one thing learning needs to know about a person: what to call them.
 *
 * Keeps certificate rendering from depending on identity's entities (§8).
 */
interface LearnerDirectory {
    fun displayNameOf(userId: UUID): String?
}
