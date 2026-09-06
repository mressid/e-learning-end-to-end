package com.elearning.shared.security

import com.elearning.shared.errors.ForbiddenException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

/**
 * "Does the caller hold this platform permission?"
 *
 * Lives in `shared/security` and reads the token rather than a database, so
 * business modules can consult it without depending on the `admin` module (§8)
 * and without a query per authorization check.
 *
 * The answer comes from the `scope` claim of an administrator's token. A
 * learner's token carries `typ=user` and no scopes, so this is always false for
 * them - there is no path by which a learner acquires a platform permission.
 *
 * **This is a fallback, never a replacement.** Every caller checks the
 * relationship first - ownership, co-instructorship, enrolment - and consults
 * this only when that fails. Reversing the order would make a course's own
 * authorization rules irrelevant whenever an admin permission happened to
 * cover them, and co-instructorship would silently stop mattering.
 */
@Component
class PlatformAccess {

    /**
     * Refuses unless the caller holds [permission].
     *
     * 403 rather than 401 throughout: a learner's token is authenticated, it
     * simply is not for this audience, and "who you are is known and it is not
     * enough" is what 403 means. Only a missing token is 401, and the filter
     * chain has already answered that before anything reaches here.
     */
    fun require(permission: String) {
        if (!has(permission)) {
            throw ForbiddenException(
                "PERMISSION_DENIED",
                "This action requires the $permission permission",
            )
        }
    }

    fun has(permission: String): Boolean {
        val jwt = SecurityContextHolder.getContext().authentication?.principal as? Jwt ?: return false
        if (jwt.getClaimAsString(TokenSubject.CLAIM) != TokenSubject.ADMIN) return false
        val scope = jwt.getClaimAsString("scope") ?: return false
        return permission in scope.split(' ')
    }
}
