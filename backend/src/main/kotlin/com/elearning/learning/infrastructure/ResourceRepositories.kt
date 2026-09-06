package com.elearning.learning.infrastructure

import com.elearning.learning.domain.CourseResource
import com.elearning.learning.domain.CourseResourceId
import com.elearning.learning.domain.ItemResource
import com.elearning.learning.domain.ItemResourceId
import com.elearning.learning.domain.Resource
import com.elearning.learning.domain.ResourceContent
import com.elearning.learning.domain.ResourceFile
import com.elearning.learning.domain.ResourceUrl
import com.elearning.learning.domain.SectionResource
import com.elearning.learning.domain.SectionResourceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ResourceRepository : JpaRepository<Resource, UUID>

interface ResourceFileRepository : JpaRepository<ResourceFile, UUID> {
    fun existsByMediaId(mediaId: UUID): Boolean
}

interface ResourceUrlRepository : JpaRepository<ResourceUrl, UUID>

interface ResourceContentRepository : JpaRepository<ResourceContent, UUID>

interface CourseResourceRepository : JpaRepository<CourseResource, CourseResourceId> {
    fun findByIdCourseIdOrderByPosition(courseId: UUID): List<CourseResource>

    fun findByIdResourceId(resourceId: UUID): List<CourseResource>

    @Query("select coalesce(max(r.position), -1) from CourseResource r where r.id.courseId = :courseId")
    fun maxPosition(@Param("courseId") courseId: UUID): Int
}

interface SectionResourceRepository : JpaRepository<SectionResource, SectionResourceId> {
    fun findByIdSectionIdOrderByPosition(sectionId: UUID): List<SectionResource>

    fun findByIdResourceId(resourceId: UUID): List<SectionResource>

    @Query("select coalesce(max(r.position), -1) from SectionResource r where r.id.sectionId = :sectionId")
    fun maxPosition(@Param("sectionId") sectionId: UUID): Int
}

interface ItemResourceRepository : JpaRepository<ItemResource, ItemResourceId> {
    fun findByIdCourseItemIdOrderByPosition(courseItemId: UUID): List<ItemResource>

    fun findByIdResourceId(resourceId: UUID): List<ItemResource>

    @Query("select coalesce(max(r.position), -1) from ItemResource r where r.id.courseItemId = :itemId")
    fun maxPosition(@Param("itemId") itemId: UUID): Int
}
