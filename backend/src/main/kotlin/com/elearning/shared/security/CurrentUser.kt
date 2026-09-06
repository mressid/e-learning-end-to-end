package com.elearning.shared.security

import com.elearning.shared.errors.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Reads the authenticated user's id from the security context.
 *
 * Application services take the id as a parameter rather than reaching for this
 * themselves, so they stay testable without a security context. Controllers
 * resolve it once at the boundary.
 */
@Component
class CurrentUser {

    fun idOrNull(): UUID? = TokenSubject.idOf(TokenSubject.USER)

    fun requireId(): UUID = idOrNull()
        ?: throw ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication is required")
}

/**
 * Reads the authenticated *administrator*.
 *
 * Separate from [CurrentUser] on purpose. Administrators live in their own
 * table, so an admin id is meaningless as a platform user id - it matches no
 * `users` row. Both readers check the token's `typ` claim, so an admin token
 * cannot act as a learner and a learner's token cannot reach an admin endpoint,
 * whatever the ids happen to be.
 */
@Component
class CurrentAdmin {

    fun idOrNull(): UUID? = TokenSubject.idOf(TokenSubject.ADMIN)

    fun requireId(): UUID = idOrNull()
        ?: throw ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Administrator authentication is required")
}

/**
 * The `typ` claim that keeps the two token audiences apart.
 *
 * Without it a token would be nothing but a subject id, and the two identity
 * tables would be interchangeable to anything reading one.
 */
object TokenSubject {

    const val CLAIM = "typ"
    const val USER = "user"
    const val ADMIN = "admin"

    fun idOf(expectedType: String): UUID? {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt ?: return null
        if (jwt.getClaimAsString(CLAIM) != expectedType) return null
        return runCatching { UUID.fromString(jwt.subject) }.getOrNull()
    }
}

/**
 * The caller of an endpoint that both learners and administrators may reach -
 * moderating a review, revoking a certificate.
 *
 * Returns whichever identity the token carries. The id is only ever used for a
 * *relationship* check (does this person own the course, did they write this
 * thread), and an administrator's id matches no such relationship - so it fails
 * that check and falls through to the platform-permission fallback, which is
 * exactly the intended path. It is never used to attribute a row to a `users`
 * record, because an admin id is not one.
 */
@Component
class CurrentActor {

    fun requireId(): UUID = TokenSubject.idOf(TokenSubject.USER)
        ?: TokenSubject.idOf(TokenSubject.ADMIN)
        ?: throw ApiException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "Authentication is required")
}
