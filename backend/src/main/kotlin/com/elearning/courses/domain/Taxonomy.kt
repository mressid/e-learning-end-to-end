package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/**
 * Curated platform taxonomy. Categories are reference data seeded by migration,
 * not user content: a shared browse tree only works if one person decides what
 * is in it, and V1 has no admin role to grant that (§11). So the API reads
 * categories and attaches them; it does not create them.
 *
 * `parentId` is a plain UUID rather than a `Category` association - the tree is
 * two levels deep in practice and a self-join mapping would invite lazy-loading
 * a whole branch to render one breadcrumb.
 */
@Entity
@Table(name = "categories")
class Category(

    @Id
    @GeneratedValue
    var id: UUID? = null,

    @Column(name = "parent_id")
    var parentId: UUID? = null,

    @Column(nullable = false, length = 160)
    var name: String = "",

    @Column(nullable = false, length = 160)
    var slug: String = "",
)

/**
 * Free-form labels. Unlike categories these are created on demand by whoever is
 * tagging their own course: a tag is a keyword, not a shared browse structure,
 * so letting editors mint them needs no admin concept.
 */
@Entity
@Table(name = "tags")
class Tag(

    @Id
    @GeneratedValue
    var id: UUID? = null,

    @Column(nullable = false, length = 80)
    var name: String = "",

    @Column(nullable = false, length = 80)
    var slug: String = "",
)

@Entity
@Table(name = "course_categories")
class CourseCategory(@EmbeddedId val id: CourseCategoryId)

@Embeddable
data class CourseCategoryId(
    @Column(name = "course_id") val courseId: UUID,
    @Column(name = "category_id") val categoryId: UUID,
) : Serializable

@Entity
@Table(name = "course_tags")
class CourseTag(@EmbeddedId val id: CourseTagId)

@Embeddable
data class CourseTagId(
    @Column(name = "course_id") val courseId: UUID,
    @Column(name = "tag_id") val tagId: UUID,
) : Serializable
