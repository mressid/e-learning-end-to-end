package com.elearning.identity.infrastructure

import com.elearning.identity.domain.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

/**
 * Any platform account, whichever kind it is.
 *
 * Polymorphic on purpose: signing in, looking someone up by id, and checking
 * that an address is free are all questions about an account, not about a
 * student or an instructor. The kind-specific listings live on
 * [StudentRepository] and [InstructorRepository], which read their own tables
 * instead of filtering this one.
 */
interface UserRepository : JpaRepository<User, UUID> {

    fun findByEmailIgnoreCase(email: String): Optional<User>

    fun existsByEmailIgnoreCase(email: String): Boolean

    fun existsByUsernameIgnoreCase(username: String): Boolean

}
