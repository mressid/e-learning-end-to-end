package com.elearning.shared.api

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

/**
 * Reordering the children of something.
 *
 * Lives in `shared` because course structure and quiz questions both need it;
 * putting it in either module would make the other depend on that module's API
 * package for a DTO that is not about its domain (§8).
 */
@Schema(name = "ReorderRequest", description = "Must list every child exactly once")
data class ReorderRequest(val orderedIds: List<UUID> = emptyList())
