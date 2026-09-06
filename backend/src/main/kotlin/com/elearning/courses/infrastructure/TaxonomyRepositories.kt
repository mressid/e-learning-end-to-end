package com.elearning.courses.infrastructure

import com.elearning.courses.application.CourseTermRow
import com.elearning.courses.domain.Category
import com.elearning.courses.domain.CourseCategory
import com.elearning.courses.domain.CourseCategoryId
import com.elearning.courses.domain.CourseTag
import com.elearning.courses.domain.CourseTagId
import com.elearning.courses.domain.Tag
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CategoryRepository : JpaRepository<Category, UUID> {

    fun findAllByOrderByNameAsc(): List<Category>

    fun findBySlug(slug: String): Category?

    fun existsByParentId(parentId: UUID): Boolean
}

interface TagRepository : JpaRepository<Tag, UUID> {

    fun findAllByOrderByNameAsc(): List<Tag>

    fun findBySlugIn(slugs: Collection<String>): List<Tag>
}

interface CourseCategoryRepository : JpaRepository<CourseCategory, CourseCategoryId> {

    fun deleteByIdCourseId(courseId: UUID)

    fun countByIdCategoryId(categoryId: UUID): Long

    /**
     * Every course's categories in one query.
     *
     * The listing endpoint renders a page of courses at once, so resolving
     * taxonomy per row would be the same N+1 the thumbnail lookup already
     * avoids - on the busiest endpoint on the platform.
     */
    @Query(
        """
        select new com.elearning.courses.application.CourseTermRow(
            cc.id.courseId, c.id, c.name, c.slug
        )
        from CourseCategory cc join Category c on c.id = cc.id.categoryId
        where cc.id.courseId in :courseIds
        order by c.name asc
        """,
    )
    fun findTermsForCourses(@Param("courseIds") courseIds: Collection<UUID>): List<CourseTermRow>
}

interface CourseTagRepository : JpaRepository<CourseTag, CourseTagId> {

    fun deleteByIdCourseId(courseId: UUID)

    @Query(
        """
        select new com.elearning.courses.application.CourseTermRow(
            ct.id.courseId, t.id, t.name, t.slug
        )
        from CourseTag ct join Tag t on t.id = ct.id.tagId
        where ct.id.courseId in :courseIds
        order by t.name asc
        """,
    )
    fun findTermsForCourses(@Param("courseIds") courseIds: Collection<UUID>): List<CourseTermRow>
}
