package com.elearning.identity.infrastructure

import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmailIgnoreCase(email: String): Optional<User>

    fun existsByEmailIgnoreCase(email: String): Boolean

    fun existsByUsernameIgnoreCase(username: String): Boolean

    fun findByStatus(status: UserStatus, pageable: Pageable): Page<User>

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
        select u from User u
        where lower(u.email) like lower(concat('%', :term, '%'))
           or lower(u.username) like lower(concat('%', :term, '%'))
        """,
    )
    fun search(@Param("term") term: String, pageable: Pageable): Page<User>
}
