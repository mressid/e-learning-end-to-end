package com.elearning.courses.domain

import jakarta.persistence.Column
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Embeddable
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/** Co-teachers. The owner is on `courses.owner_id` and is not duplicated here. */
@Entity
@Table(name = "course_instructors")
class CourseInstructor(

    @EmbeddedId
    val id: CourseInstructorId,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: InstructorRole = InstructorRole.ASSISTANT,
)

@Embeddable
data class CourseInstructorId(
    @Column(name = "course_id") val courseId: UUID,
    @Column(name = "instructor_id") val instructorId: UUID,
) : Serializable
