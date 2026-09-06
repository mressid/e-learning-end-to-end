package com.elearning.learning.infrastructure

import com.elearning.courses.application.ItemActivityProbe
import com.elearning.learning.infrastructure.LearningProgressRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** Learning's answer: a student has a progress record against the item. */
@Component
class ProgressActivityProbe(
    private val progress: LearningProgressRepository,
) : ItemActivityProbe {

    override fun hasStudentActivity(itemIds: Collection<UUID>): Boolean =
        itemIds.isNotEmpty() && progress.existsByCourseItemIdIn(itemIds)

    override fun describe(): String = "student progress"
}
