package com.elearning.courses.infrastructure

import com.elearning.courses.domain.Course
import com.elearning.courses.domain.CourseInstructor
import com.elearning.courses.domain.CourseInstructorId
import com.elearning.courses.domain.CourseItem
import com.elearning.courses.domain.CourseSection
import com.elearning.courses.domain.CourseStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Projection for the instructor roster. */
interface OwnerCounts {
    val instructorId: UUID
    val courseCount: Long
    val publishedCount: Long
}

interface InstructorRow {
    val instructorId: UUID
    val courseCount: Long
}

interface CourseRepository : JpaRepository<Course, UUID> {

    fun findByStatus(status: CourseStatus, pageable: Pageable): Page<Course>

    fun existsBySlug(slug: String): Boolean

    fun existsByThumbnailMediaId(thumbnailMediaId: UUID): Boolean

    /**
     * Everyone who owns at least one course, with how many they own.
     *
     * "Instructor" is not a role on this platform - it is a relationship (§11) -
     * so the roster is derived from ownership rather than read off a column.
     * Co-instructors are counted separately by `CourseInstructorRepository`,
     * because holding a seat on somebody else's course is a different fact from
     * running your own.
     */
    @Query(
        """
        select c.ownerId as instructorId, count(c) as courseCount
        from Course c group by c.ownerId order by count(c) desc
        """,
        countQuery = "select count(distinct c.ownerId) from Course c",
    )
    fun findOwners(pageable: Pageable): Page<InstructorRow>

    /**
     * Course counts for a page of instructors, in one grouped query.
     *
     * Replaces a `countByOwnerAndStatus` per row - twenty instructors meant
     * forty queries, which is the N+1 the roster already avoided for names.
     */
    @Query(
        """
        select c.ownerId as instructorId,
               count(c) as courseCount,
               sum(case when c.status = com.elearning.courses.domain.CourseStatus.PUBLISHED
                        then 1L else 0L end) as publishedCount
        from Course c
        where c.ownerId in :ownerIds
        group by c.ownerId
        """,
    )
    fun countsForOwners(@Param("ownerIds") ownerIds: Collection<UUID>): List<OwnerCounts>

    /**
     * Title search across **every** status, for the dashboard.
     *
     * A plain LIKE rather than the `search_vector` index: that one is built for
     * discovery ranking and is filtered to PUBLISHED at the query site, while an
     * administrator looking for a half-finished draft wants a substring match on
     * an occasionally-used page. Ranking would be noise here.
     */
    @Query(
        """
        select c from Course c
        where lower(c.title) like lower(concat('%', :term, '%'))
        """,
    )
    fun searchAllStatuses(@Param("term") term: String, pageable: Pageable): Page<Course>

    fun findByOwnerId(ownerId: UUID, pageable: Pageable): Page<Course>

    /**
     * Everything one instructor is responsible for, at any status.
     *
     * Owned *or* co-instructed, because both confer authority over the course
     * (AGENTS.md §11) and an instructor opening their own workspace expects to
     * see the courses they can actually edit — not just the ones they started.
     *
     * Unlike the public listing this ignores status: drafts are the whole point
     * of the page, since a course nobody has published yet is exactly the one
     * its author still has work to do on.
     */
    @Query(
        """
        select c from Course c
        where c.ownerId = :userId
           or c.id in (select ci.id.courseId from CourseInstructor ci where ci.id.instructorId = :userId)
        """,
    )
    fun findMine(@Param("userId") userId: UUID, pageable: Pageable): Page<Course>

    fun countByStatus(status: CourseStatus): Long

    @Query("select count(c) from Course c where c.ownerId = :ownerId and c.status = :status")
    fun countByOwnerAndStatus(
        @Param("ownerId") ownerId: UUID,
        @Param("status") status: CourseStatus,
    ): Long

    /**
     * Full-text search over the generated `search_vector` column.
     *
     * Native because `tsvector`/`@@` have no JPQL equivalent, and this is
     * exactly the case AGENT.md §10 calls out: a custom query is preferable to
     * bending a generic abstraction around it. `plainto_tsquery` treats input
     * as literal words, so user text cannot inject query operators.
     */
    @Query(
        value = """
            SELECT * FROM courses
            WHERE status = :status
              AND search_vector @@ plainto_tsquery('simple', :term)
            ORDER BY ts_rank(search_vector, plainto_tsquery('simple', :term)) DESC
        """,
        countQuery = """
            SELECT count(*) FROM courses
            WHERE status = :status
              AND search_vector @@ plainto_tsquery('simple', :term)
        """,
        nativeQuery = true,
    )
    fun search(
        @Param("term") term: String,
        @Param("status") status: String,
        pageable: Pageable,
    ): Page<Course>
}

interface CourseSectionRepository : JpaRepository<CourseSection, UUID> {

    fun findByCourseIdOrderByPosition(courseId: UUID): List<CourseSection>

    @Query("select coalesce(max(s.position), -1) from CourseSection s where s.courseId = :courseId")
    fun maxPosition(@Param("courseId") courseId: UUID): Int
}

interface CourseItemRepository : JpaRepository<CourseItem, UUID> {

    fun findBySectionIdOrderByPosition(sectionId: UUID): List<CourseItem>

    @Query("select coalesce(max(i.position), -1) from CourseItem i where i.sectionId = :sectionId")
    fun maxPosition(@Param("sectionId") sectionId: UUID): Int

    @Query(
        """
        select count(i) from CourseItem i
        where i.sectionId in (select s.id from CourseSection s where s.courseId = :courseId)
        """,
    )
    fun countByCourseId(@Param("courseId") courseId: UUID): Int

    /** Resolves an item back to its course, for callers that only hold an item id. */
    @Query(
        """
        select s.courseId from CourseItem i join CourseSection s on s.id = i.sectionId
        where i.id = :itemId
        """,
    )
    fun findCourseIdOfItem(@Param("itemId") itemId: UUID): UUID?

    @Query(
        """
        select i.id from CourseItem i
        where i.sectionId in (select s.id from CourseSection s where s.courseId = :courseId)
          and i.isRequired = true
        """,
    )
    fun findRequiredItemIds(@Param("courseId") courseId: UUID): List<UUID>
}

interface CourseInstructorRepository : JpaRepository<CourseInstructor, CourseInstructorId> {

    fun findByIdCourseId(courseId: UUID): List<CourseInstructor>
}
