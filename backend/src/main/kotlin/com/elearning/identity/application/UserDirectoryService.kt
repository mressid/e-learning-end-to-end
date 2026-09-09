package com.elearning.identity.application

import com.elearning.identity.domain.Instructor
import com.elearning.identity.domain.Student
import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.domain.UserType
import com.elearning.identity.infrastructure.InstructorRepository
import com.elearning.identity.infrastructure.StudentRepository
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
    private val students: StudentRepository,
    private val instructors: InstructorRepository,
    private val profiles: UserProfileRepository,
    private val refreshTokens: RefreshTokenService,
    private val audit: AuditService,
    private val platformAccess: PlatformAccess,
    private val passwordEncoder: PasswordEncoder,
) {

    /**
     * The learners, and only the learners.
     *
     * It used to be every account, with the instructors marked by a badge. They
     * are separate kinds now, so this reads `students` and an instructor cannot
     * turn up here at all - not even if a caller forgets a filter, because there
     * is no filter to forget.
     */
    @Transactional(readOnly = true)
    fun list(term: String?, status: UserStatus?, pageable: Pageable): Page<Student> {
        platformAccess.require("user.read")
        return when {
            !term.isNullOrBlank() -> students.search(term.trim(), pageable)
            status != null -> students.findByStatus(status, pageable)
            else -> students.findAll(pageable)
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

    /** The roster: everyone whose account is an instructor account, courses or not. */
    @Transactional(readOnly = true)
    fun listInstructors(term: String?, pageable: Pageable): Page<Instructor> {
        platformAccess.require("user.read")
        return if (term.isNullOrBlank()) {
            instructors.findAll(pageable)
        } else {
            instructors.search(term.trim(), pageable)
        }
    }

    /** Whether an account may author courses - which is to say, whether it is one. */
    @Transactional(readOnly = true)
    fun isInstructor(userId: UUID): Boolean = instructors.existsById(userId)

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
     *
     * `type` is the one field that is decided here and never again. There is no
     * edit that turns a student into an instructor, so choosing wrongly means
     * creating the other account, not correcting this one.
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

        val email = command.email.lowercase()
        val hash = requireNotNull(passwordEncoder.encode(command.password))
        val user: User = when (command.type) {
            UserType.STUDENT -> students.save(
                Student(email, command.username, hash, UserStatus.ACTIVE),
            )
            UserType.INSTRUCTOR -> instructors.save(
                Instructor(email, command.username, hash, UserStatus.ACTIVE),
            )
        }
        val id = requireNotNull(user.id)

        profiles.save(
            UserProfile(user = user, firstName = command.firstName, lastName = command.lastName),
        )

        val kind = command.type.name.lowercase()
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
     * Setting a learner's or instructor's password, because they have lost it.
     *
     * The self-service route is reset-by-email, and it stays the normal one.
     * This is for when that cannot work: an instructor whose address was never
     * real, or a learner who no longer has the inbox. An administrator sets the
     * password and hands it over, exactly as they would for a new account.
     *
     * Gated on `user.write` like the rest of the directory - the same permission
     * that can already change the address a reset email would go to, so guarding
     * this more tightly would protect nothing.
     *
     * Their sessions end. A forgotten password often means a shared or leaked
     * one, and leaving the old sessions alive would let whoever has it stay.
     */
    @Transactional
    fun setPassword(userId: UUID, newPassword: String): User {
        platformAccess.require("user.write")
        val user = users.findById(userId)
            .orElseThrow { NotFoundException("USER_NOT_FOUND", "User not found") }

        user.passwordHash = requireNotNull(passwordEncoder.encode(newPassword))
        user.updatedAt = Instant.now()
        refreshTokens.revokeAllForUser(userId)

        audit.record(
            action = "user.password_reset",
            summary = "Reset the password of ${user.email}",
            targetType = "USER",
            targetId = userId,
            details = mapOf("type" to user.type.name),
        )
        return user
    }

    /**
     * Edits an account. Every field is optional; only what is supplied changes.
     *
     * What it cannot change is which kind of account this is. That used to be a
     * boolean here, so an administrator could turn a learner into an author and
     * back - which made "instructor" a setting rather than a fact, and left the
     * question "is this person an author" with a different answer depending on
     * when you asked. A learner who starts teaching gets an instructor account;
     * their learning history stays on the account that earned it.
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
    /** Settled here and permanent; see [UserDirectoryService.create]. */
    val type: UserType,
)

data class UpdateUserCommand(
    val email: String?,
    val username: String?,
    val firstName: String?,
    val lastName: String?,
)
