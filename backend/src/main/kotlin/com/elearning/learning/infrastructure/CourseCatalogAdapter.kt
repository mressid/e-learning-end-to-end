package com.elearning.learning.infrastructure

import com.elearning.courses.application.CourseAuthorization
import com.elearning.courses.domain.CourseStatus
import com.elearning.courses.infrastructure.CourseItemRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.courses.infrastructure.ItemPrerequisiteRepository
import com.elearning.learning.application.CourseCatalog
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The one place the learning module reaches into courses. Keeping it here means
 * a change to the course model touches this adapter, not the learning use cases.
 */
@Component
class CourseCatalogAdapter(
    private val courses: CourseRepository,
    private val items: CourseItemRepository,
    private val itemPrerequisites: ItemPrerequisiteRepository,
    private val authorization: CourseAuthorization,
) : CourseCatalog {

    override fun exists(courseId: UUID): Boolean = courses.existsById(courseId)

    override fun isPublished(courseId: UUID): Boolean =
        courses.findById(courseId).map { it.status == CourseStatus.PUBLISHED }.orElse(false)

    override fun courseIdOfItem(itemId: UUID): UUID? = items.findCourseIdOfItem(itemId)

    override fun requiredItemIds(courseId: UUID): List<UUID> = items.findRequiredItemIds(courseId)

    override fun canEdit(courseId: UUID, userId: UUID): Boolean =
        courses.findById(courseId).map { authorization.canEdit(it, userId) }.orElse(false)

    override fun itemType(itemId: UUID): String? = items.findById(itemId).map { it.type.name }.orElse(null)

    override fun courseTitle(courseId: UUID): String? = courses.findById(courseId).map { it.title }.orElse(null)

    override fun itemTitle(itemId: UUID): String? = items.findById(itemId).map { it.title }.orElse(null)

    override fun accessDurationDays(courseId: UUID): Int? =
        courses.findById(courseId).map { it.accessDurationDays }.orElse(null)

    override fun prerequisiteItemIds(itemId: UUID): List<UUID> =
        itemPrerequisites.prerequisiteIdsOf(itemId)
}
