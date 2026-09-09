package com.elearning.identity.infrastructure

import com.elearning.identity.domain.Student
import com.elearning.identity.domain.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * The learner directory.
 *
 * Every query here is over `students` joined to `users`, so an instructor
 * cannot appear in a learner listing by omission - there is no flag to forget
 * to filter on. That was the problem with the previous shape: the directory
 * listed the whole user table and marked the instructors with a badge.
 */
interface StudentRepository : JpaRepository<Student, UUID> {

    fun findByStatus(status: UserStatus, pageable: Pageable): Page<Student>

    /**
     * Directory search over the fields an administrator actually has to hand:
     * an address or a username from a support ticket.
     *
     * Deliberately not the course full-text index - that is a `tsvector` built
     * for discovery ranking, and matching a partial email against it would need
     * a second index on `users` for a page that is used occasionally.
     */
    @Query(
        """
        select s from Student s
        where lower(s.email) like lower(concat('%', :term, '%'))
           or lower(s.username) like lower(concat('%', :term, '%'))
        """,
    )
    fun search(@Param("term") term: String, pageable: Pageable): Page<Student>
}
