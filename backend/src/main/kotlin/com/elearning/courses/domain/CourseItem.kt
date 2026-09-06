package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * The ordered unit of learning inside a section.
 *
 * Lessons, quizzes and assignments all appear here so a section has one
 * sequence rather than three parallel ones. The type-specific tables
 * (`lessons`, `quizzes`, `assignments`) hang off this row and belong to the
 * learning and assessment modules.
 */
@Entity
@Table(name = "course_items")
class CourseItem(

    @Column(name = "section_id", nullable = false, updatable = false)
    val sectionId: UUID,

    @Column(nullable = false)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val type: CourseItemType,

    @Column(nullable = false)
    var position: Int,

    @Column(name = "is_required", nullable = false)
    var isRequired: Boolean = true,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null

    @Column(name = "available_from")
    var availableFrom: Instant? = null

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
}
