package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "course_sections")
class CourseSection(

    @Column(name = "course_id", nullable = false, updatable = false)
    val courseId: UUID,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var position: Int,

    @Column
    var description: String? = null,
) {
    @Id
    @GeneratedValue
    var id: UUID? = null
}
