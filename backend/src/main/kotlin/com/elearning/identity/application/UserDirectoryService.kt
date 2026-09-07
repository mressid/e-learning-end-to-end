package com.elearning.identity.application

import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import com.elearning.shared.errors.BusinessRuleException
import com.elearning.shared.errors.ConflictException
import com.elearning.shared.errors.NotFoundException
import com.elearning.shared.security.PlatformAccess
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import com.elearning.platform.audit.AuditService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * The learner directory behind the dashboard's `/students` page.
 *
 * Every method demands a platform permission. Unlike a course, a directory has
 * no relationship to derive authority from - there is no edge between an
 * administrator and "all users" - so a permission is the only honest way to
 * express it (§11).
 */
@Service
class UserDirectoryService(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val platformAccess: PlatformAccess,
    private val passwordEncoder: PasswordEncoder,
) {

    @Transactional(readOnly = true)
    fun list(term: String?, status: UserStatus?, pageable: Pageable): Page<User> {
        platformAccess.require("user.read")
        return when {
            !term.isNullOrBlank() -> users.search(term.trim(), pageable)
            status != null -> users.findByStatus(status, pageable)
            else -> users.findAll(pageable)
        }
    }

    @Transactional(readOnly = true)
    fun get(userId: UUID): User {
        platformAccess.require("user.read")
        return users.findById(userId).orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }
    }

    /** Profiles for a page of users, in one query rather than one per row. */
    @Transactional(readOnly = true)
    fun profilesFor(userIds: Collection<UUID>): Map<UUID, UserProfile> =
        if (userIds.isEmpty()) {
            emptyMap()
        } else {
            profiles.findAllById(userIds).associateBy { requireNotNull(it.userId) }
        }

    /**
     * Suspends or reinstates a learner.
     *
     * Suspension takes effect at the account's next sign-in or token refresh,
     * because a signed access token cannot be withdrawn - the same property
     * logout already lives with. Their existing sessions are revoked so the
     * window is one access-token lifetime rather than the refresh token's month.
     */
    @Transactional
    fun setStatus(userId: UUID, status: UserStatus): User {
        platformAccess.require("user.suspend")
        val user = users.findById(userId)
            .orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }

        // PENDING means "has not confirmed their address yet" and is reached by
        // registering, not by an administrator deciding it. Allowing it here
        // would let staff push a verified account back into a state only the
        // verification flow is supposed to produce.
        if (status == UserStatus.PENDING) {
            throw BusinessRuleException(
                "INVALID_STATUS",
                "PENDING is set by registration, not by an administrator",
            )
        }

        val previous = user.status
        user.status = status
        user.updatedAt = Instant.now()
        if (status != UserStatus.ACTIVE) refreshTokens.revokeAllForUser(userId)

        audit.record(
            action = "user.status_changed",
            summary = "Changed ${user.email} from $previous to $status",
            targetType = "USER",
            targetId = userId,
            details = mapOf("from" to previous.name, "to" to status.name),
        )
        return user
    }

    /** Instructors, flagged rather than inferred - so one with no courses still appears. */
    @Transactional(readOnly = true)
    fun listInstructors(term: String?, pageable: Pageable): Page<User> {
        platformAccess.require("user.read")
        return if (term.isNullOrBlank()) {
            users.findByIsInstructorTrue(pageable)
        } else {
            users.searchInstructors(term.trim(), pageable)
        }
    }

    /**
     * Creates an account on someone's behalf.
     *
     * ACTIVE immediately, unlike registration: an administrator typing the
     * address *is* the verification, and leaving the account PENDING would mean
     * the person cannot sign in until an email they never expected arrives.
     *
     * The password is a starting one. Nothing here forces a change on first
     * sign-in, because no such mechanism exists yet - say so when handing it
     * over rather than assuming the system will ask.
     */
    @Transactional
    fun create(command: CreateUserCommand): User {
        platformAccess.require("user.write")

        // Checked for a precise error code; the unique constraints remain the
        // actual guarantee against a race.
        if (users.existsByEmailIgnoreCase(command.email)) {
            throw ConflictException("EMAIL_ALREADY_REGISTERED", "That email address is already registered")
        }
        if (users.existsByUsernameIgnoreCase(command.username)) {
            throw ConflictException("USERNAME_TAKEN", "That username is already taken")
        }

        val user = users.save(
            User(
                email = command.email.lowercase(),
                username = command.username,
                passwordHash = requireNotNull(passwordEncoder.encode(command.password)),
                status = UserStatus.ACTIVE,
                isInstructor = command.isInstructor,
            ),
        )
        val id = requireNotNull(user.id)

        profiles.save(
            UserProfile(user = user, firstName = command.firstName, lastName = command.lastName),
        )

        val kind = if (command.isInstructor) "instructor" else "learner"
        audit.record(
            action = "$kind.created",
            summary = "Created $kind ${user.email}",
            targetType = "USER",
            targetId = id,
            details = mapOf("email" to user.email, "username" to user.username),
        )
        return user
    }

    /**
     * Edits an account. Every field is optional; only what is supplied changes.
     *
     * Clearing `isInstructor` does **not** touch the courses they already own.
     * Ownership is the authority over those (§11), and revoking the flag only
     * stops them starting new ones - orphaning live courses to tidy a flag would
     * be a far larger act than the one being asked for.
     */
    @Transactional
    fun update(userId: UUID, command: UpdateUserCommand): User {
        platformAccess.require("user.write")
        val user = users.findById(userId)
            .orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }

        val changed = mutableMapOf<String, Any>()

        command.email?.let { raw ->
            val email = raw.lowercase()
            if (email != user.email) {
                if (users.existsByEmailIgnoreCase(email)) {
                    throw ConflictException("EMAIL_ALREADY_REGISTERED", "That email address is already registered")
                }
                changed["email"] = email
                user.email = email
            }
        }

        command.username?.let { username ->
            if (username != user.username) {
                if (users.existsByUsernameIgnoreCase(username)) {
                    throw ConflictException("USERNAME_TAKEN", "That username is already taken")
                }
                changed["username"] = username
                user.username = username
            }
        }

        command.isInstructor?.let { flag ->
            if (flag != user.isInstructor) {
                changed["isInstructor"] = flag
                user.isInstructor = flag
            }
        }

        if (command.firstName != null || command.lastName != null) {
            val profile = profiles.findById(userId).orElseGet {
                profiles.save(UserProfile(user = user))
            }
            command.firstName?.let { profile.firstName = it; changed["firstName"] = it }
            command.lastName?.let { profile.lastName = it; changed["lastName"] = it }
        }

        if (changed.isEmpty()) return user

        user.updatedAt = Instant.now()
        audit.record(
            action = "user.updated",
            summary = "Updated ${user.email}",
            targetType = "USER",
            targetId = userId,
            details = changed.toMap(),
        )
        return user
    }
}

data class CreateUserCommand(
    val email: String,
    val username: String,
    val password: String,
    val firstName: String?,
    val lastName: String?,
    val isInstructor: Boolean,
)

data class UpdateUserCommand(
    val email: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
    val isInstructor: Boolean?,
)
