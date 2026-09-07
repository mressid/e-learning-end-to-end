package com.elearning.courses.application

import com.elearning.courses.domain.Category
import com.elearning.courses.domain.CourseCategory
import com.elearning.courses.domain.CourseCategoryId
import com.elearning.courses.domain.CourseTag
import com.elearning.courses.domain.CourseTagId
import com.elearning.courses.domain.Tag
import com.elearning.courses.infrastructure.CategoryRepository
import com.elearning.courses.infrastructure.CourseCategoryRepository
import com.elearning.courses.infrastructure.CourseRepository
import com.elearning.courses.infrastructure.CourseTagRepository
import com.elearning.courses.infrastructure.TagRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import com.elearning.platform.audit.AuditService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** One (course, term) pair, as returned by the batched lookups. */
data class CourseTermRow(val courseId: UUID, val termId: UUID, val name: String, val slug: String)

/** A category or tag as the API renders it. */
data class TermView(val id: UUID, val name: String, val slug: String)

/**
 * Categories and tags on a course.
 *
 * The two are deliberately not symmetrical:
 *
 * - **Categories** are curated: a shared browse tree only means something if one
 *   authority decides what is in it. That authority now exists - the
 *   `category.manage` permission - so the tree is editable by administrators
 *   and by nobody else. Course editors still only *attach* what is already
 *   there, and attaching an unknown id is a 404 rather than a quiet create,
 *   which is what a typo deserves.
 * - **Tags** are free-form keywords minted on demand by whoever is tagging
 *   their own course. That needs no admin concept, because a tag structures
 *   nothing outside the courses that carry it.
 *
 * Both are set wholesale rather than added one at a time: an editor picking
 * categories in a form sends the list they want, and a PUT that replaces makes
 * removing the last one expressible. Add/remove endpoints cannot express that.
 */
@Service
class TaxonomyService(
    private val courses: CourseRepository,
    private val authorization: CourseAuthorization,
    private val categories: CategoryRepository,
    private val tags: TagRepository,
    private val courseCategories: CourseCategoryRepository,
    private val courseTags: CourseTagRepository,
    private val platformAccess: PlatformAccess,
    private val audit: AuditService,
) {

    @Transactional(readOnly = true)
    fun listCategories(): List<Category> = categories.findAllByOrderByNameAsc()

    @Transactional(readOnly = true)
    fun listTags(): List<Tag> = tags.findAllByOrderByNameAsc()

    @Transactional
    fun setCategories(courseId: UUID, categoryIds: List<UUID>, editorId: UUID): List<TermView> {
        requireEditor(courseId, editorId)

        val wanted = categoryIds.distinct()
        val found = categories.findAllById(wanted).associateBy { requireNotNull(it.id) }
        // Naming a category that does not exist is a mistake worth reporting:
        // silently dropping it would leave the editor believing it was applied.
        val missing = wanted.filterNot(found::containsKey)
        if (missing.isNotEmpty()) {
            throw NotFoundException("CATEGORY_NOT_FOUND", "No category with id ${missing.first()}")
        }

        courseCategories.deleteByIdCourseId(courseId)
        // The delete and the inserts share a transaction and both touch the
        // composite primary key, so the flush has to happen before the rows
        // are written back or the insert collides with the row being removed.
        courseCategories.flush()
        courseCategories.saveAll(wanted.map { CourseCategory(CourseCategoryId(courseId, it)) })

        return wanted.map { found.getValue(it) }
            .sortedBy(Category::name)
            .map { TermView(requireNotNull(it.id), it.name, it.slug) }
    }

    @Transactional
    fun setTags(courseId: UUID, names: List<String>, editorId: UUID): List<TermView> {
        requireEditor(courseId, editorId)

        // Slug is the identity: "Machine Learning", "machine learning" and
        // "Machine-Learning" are one tag, or the tag list becomes a list of
        // near-duplicates that no filter can usefully group.
        val bySlug = LinkedHashMap<String, String>()
        names.forEach { raw ->
            val slug = SlugGenerator.slugify(raw, TAG_SLUG_MAX)
            if (slug.isNotBlank()) bySlug.putIfAbsent(slug, raw.trim().take(TAG_NAME_MAX))
        }
        if (bySlug.size > MAX_TAGS_PER_COURSE) {
            throw BusinessRuleException(
                "TOO_MANY_TAGS",
                "A course may carry at most $MAX_TAGS_PER_COURSE tags",
            )
        }

        val existing = tags.findBySlugIn(bySlug.keys).associateBy(Tag::slug)
        val resolved = bySlug.map { (slug, name) ->
            existing[slug] ?: tags.save(Tag(name = name, slug = slug))
        }

        courseTags.deleteByIdCourseId(courseId)
        courseTags.flush()
        courseTags.saveAll(resolved.map { CourseTag(CourseTagId(courseId, requireNotNull(it.id))) })

        return resolved.sortedBy(Tag::name).map { TermView(requireNotNull(it.id), it.name, it.slug) }
    }

    /** Categories for a page of courses, in one query each. */
    @Transactional(readOnly = true)
    fun categoriesFor(courseIds: Collection<UUID>): Map<UUID, List<TermView>> =
        group(if (courseIds.isEmpty()) emptyList() else courseCategories.findTermsForCourses(courseIds))

    @Transactional(readOnly = true)
    fun tagsFor(courseIds: Collection<UUID>): Map<UUID, List<TermView>> =
        group(if (courseIds.isEmpty()) emptyList() else courseTags.findTermsForCourses(courseIds))

    private fun group(rows: List<CourseTermRow>): Map<UUID, List<TermView>> =
        rows.groupBy(CourseTermRow::courseId) { TermView(it.termId, it.name, it.slug) }

    private fun requireEditor(courseId: UUID, editorId: UUID) {
        val course = courses.findById(courseId)
            .orElseThrow { NotFoundException("COURSE_NOT_FOUND", "Course not found") }
        authorization.requireCanEdit(course, editorId)
    }

    // ---- the curated tree, for administrators ---------------------------

    /**
     * Adds a category.
     *
     * Seeding remains the source of the base tree (§9); this is for the
     * additions a running platform accumulates, which should not each require
     * a migration.
     */
    @Transactional
    fun createCategory(name: String, parentId: UUID?): Category {
        platformAccess.require("category.manage")

        val slug = SlugGenerator.slugify(name, CATEGORY_SLUG_MAX).ifBlank { "category" }
        if (categories.findBySlug(slug) != null) {
            throw ConflictException("CATEGORY_EXISTS", "A category named \"$name\" already exists")
        }
        if (parentId != null && categories.findById(parentId).isEmpty) {
            throw NotFoundException("CATEGORY_NOT_FOUND", "No category with id $parentId")
        }
        return categories.save(Category(parentId = parentId, name = name, slug = slug))
    }

    /**
     * Renames a category, leaving its slug alone.
     *
     * The slug is the identity the seeder matches on and the value a filter URL
     * carries, so regenerating it on a rename would both orphan the seed entry
     * and break any link already shared - the same rule course slugs follow.
     */
    @Transactional
    fun renameCategory(categoryId: UUID, name: String): Category {
        platformAccess.require("category.manage")
        val category = categories.findById(categoryId)
            .orElseThrow { NotFoundException("CATEGORY_NOT_FOUND", "No such category") }
        category.name = name
        return category
    }

    /**
     * Removes a category, but only once nothing depends on it.
     *
     * Both foreign keys pointing here cascade: children vanish with their
     * parent, and `course_categories` rows go with them - so a delete that the
     * database allows would silently strip categorisation from every course
     * carrying it. Refusing while it is in use is the only honest behaviour.
     */
    @Transactional
    fun deleteCategory(categoryId: UUID) {
        platformAccess.require("category.manage")
        val category = categories.findById(categoryId)
            .orElseThrow { NotFoundException("CATEGORY_NOT_FOUND", "No such category") }

        if (categories.existsByParentId(categoryId)) {
            throw BusinessRuleException(
                "CATEGORY_HAS_CHILDREN",
                "Remove or re-parent its subcategories first",
            )
        }
        val inUse = courseCategories.countByIdCategoryId(categoryId)
        if (inUse > 0) {
            throw BusinessRuleException(
                "CATEGORY_IN_USE",
                "$inUse course(s) are still in this category",
            )
        }
        audit.record(
            action = "category.deleted",
            summary = "Deleted category \"${category.name}\"",
            targetType = "CATEGORY",
            targetId = categoryId,
        )
        categories.delete(category)
    }

    private companion object {
        const val CATEGORY_SLUG_MAX = 160
        const val TAG_SLUG_MAX = 80
        const val TAG_NAME_MAX = 80
        const val MAX_TAGS_PER_COURSE = 25
    }
}
