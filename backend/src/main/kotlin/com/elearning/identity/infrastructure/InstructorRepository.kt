package com.elearning.identity.infrastructure

import com.elearning.identity.domain.Instructor
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * The instructor roster.
 *
 * `findAll` is the whole roster, because the table holds instructors and
 * nothing else - which is why the partial index V11 needed on `users` is gone.
 * Someone appointed this morning is here before they have authored anything,
 * which is exactly the person an administrator has just created.
 */
interface InstructorRepository : JpaRepository<Instructor, UUID> {

    @Query(
        """
        select i from Instructor i
        where lower(i.email) like lower(concat('%', :term, '%'))
           or lower(i.username) like lower(concat('%', :term, '%'))
        """,
    )
    fun search(@Param("term") term: String, pageable: Pageable): Page<Instructor>
}
