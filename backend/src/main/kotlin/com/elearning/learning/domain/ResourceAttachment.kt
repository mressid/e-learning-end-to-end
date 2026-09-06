package com.elearning.learning.domain

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.io.Serializable
import java.util.UUID

/**
 * Why a resource is attached. Mirrors the shared `relationship_type` column.
 */
enum class RelationshipType {
    RESOURCE,
    REFERENCE,
    ATTACHMENT,
    SUPPLEMENTARY,
    REQUIRED,
    SOLUTION,
    EXAMPLE,
    READING,
}

/**
 * Attachment at the three scopes the data model defines: course, section, item.
 *
 * Three tables rather than one polymorphic table with a nullable owner column,
 * so each foreign key is real and the database can enforce it.
 */
@Entity
@Table(name = "course_resources")
class CourseResource(
    @EmbeddedId val id: CourseResourceId,

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false)
    var relationshipType: RelationshipType = RelationshipType.RESOURCE,

    @Column(nullable = false)
    var position: Int = 0,
)

@Embeddable
data class CourseResourceId(
    @Column(name = "course_id") val courseId: UUID,
    @Column(name = "resource_id") val resourceId: UUID,
) : Serializable

@Entity
@Table(name = "section_resources")
class SectionResource(
    @EmbeddedId val id: SectionResourceId,

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false)
    var relationshipType: RelationshipType = RelationshipType.RESOURCE,

    @Column(nullable = false)
    var position: Int = 0,
)

@Embeddable
data class SectionResourceId(
    @Column(name = "section_id") val sectionId: UUID,
    @Column(name = "resource_id") val resourceId: UUID,
) : Serializable

@Entity
@Table(name = "item_resources")
class ItemResource(
    @EmbeddedId val id: ItemResourceId,

    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false)
    var relationshipType: RelationshipType = RelationshipType.RESOURCE,

    @Column(nullable = false)
    var position: Int = 0,
)

@Embeddable
data class ItemResourceId(
    @Column(name = "course_item_id") val courseItemId: UUID,
    @Column(name = "resource_id") val resourceId: UUID,
) : Serializable
