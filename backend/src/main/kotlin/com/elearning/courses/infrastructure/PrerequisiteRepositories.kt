package com.elearning.courses.infrastructure

import com.elearning.courses.domain.CoursePrerequisiteNote
import com.elearning.courses.domain.ItemPrerequisite
import com.elearning.courses.domain.ItemPrerequisiteId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface CoursePrerequisiteNoteRepository : JpaRepository<CoursePrerequisiteNote, UUID> {

    fun findByCourseIdOrderByPosition(courseId: UUID): List<CoursePrerequisiteNote>

    fun deleteByCourseId(courseId: UUID)
}

interface ItemPrerequisiteRepository : JpaRepository<ItemPrerequisite, ItemPrerequisiteId> {

    fun deleteByIdItemId(itemId: UUID)

    @Query("select p.id.prerequisiteItemId from ItemPrerequisite p where p.id.itemId = :itemId")
    fun prerequisiteIdsOf(@Param("itemId") itemId: UUID): List<UUID>

    /**
     * One level of the graph for a whole frontier at once.
     *
     * The cycle walk explores breadth-first, so asking per node would issue a
     * query per edge; this keeps it to one query per level instead.
     */
    @Query("select p.id.prerequisiteItemId from ItemPrerequisite p where p.id.itemId in :itemIds")
    fun prerequisiteIdsOfAll(@Param("itemIds") itemIds: Collection<UUID>): List<UUID>
}
