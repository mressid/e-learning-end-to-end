package com.elearning.courses.application

import java.util.UUID

/**
 * "Has any student touched these items?"
 *
 * Declared by courses, answered by whoever owns the student data - learning
 * holds progress, assessment holds attempts and submissions. Courses depends on
 * neither: it asks a question and each module that has an answer registers one
 * of these (§8). Adding a new kind of student record means adding a probe, not
 * editing the delete path.
 *
 * Asked as a batch because deleting a section deletes every item under it, and
 * one question per item would be a query per row.
 */
interface ItemActivityProbe {

    /** True if any of [itemIds] carries work that deleting would destroy. */
    fun hasStudentActivity(itemIds: Collection<UUID>): Boolean

    /** Named in the refusal, so the editor is told what is in the way. */
    fun describe(): String
}
