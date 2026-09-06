package com.elearning.platform.media

import java.util.UUID

/**
 * "Is anything still pointing at this file?"
 *
 * Declared by `platform/media`, answered by whoever holds a reference. Media
 * knows it is stored; it does not know a lesson plays it or a certificate is
 * it, and it should not have to (§8).
 *
 * This exists because the **database will not stop the delete**. Almost every
 * reference to `media_objects` is `ON DELETE SET NULL`, so removing a file that
 * a lesson uses does not fail - it quietly empties the lesson's video and
 * leaves a course that plays nothing. Only `resource_files` is RESTRICT.
 * Silence is the failure mode, which is why the check is up front.
 */
interface MediaReferenceProbe {

    /** True if [mediaId] is still in use here. */
    fun isReferenced(mediaId: UUID): Boolean

    /** Named in the refusal, so an administrator is told what is in the way. */
    fun describe(): String
}
