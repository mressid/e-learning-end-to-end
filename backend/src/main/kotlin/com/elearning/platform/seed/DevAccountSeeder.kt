package com.elearning.platform.seed

import com.elearning.admin.domain.AdminUser
import com.elearning.admin.domain.AdminUserRole
import com.elearning.admin.domain.AdminUserRoleId
import com.elearning.admin.domain.Role
import com.elearning.admin.domain.RolePermission
import com.elearning.admin.domain.RolePermissionId
import com.elearning.admin.infrastructure.AdminUserRepository
import com.elearning.admin.infrastructure.AdminUserRoleRepository
import com.elearning.admin.infrastructure.PermissionRepository
import com.elearning.admin.infrastructure.RolePermissionRepository
import com.elearning.admin.infrastructure.RoleRepository
import com.elearning.identity.domain.Instructor
import com.elearning.identity.domain.Student
import com.elearning.identity.domain.User
import com.elearning.identity.domain.UserProfile
import com.elearning.identity.domain.UserStatus
import com.elearning.identity.infrastructure.UserProfileRepository
import com.elearning.identity.infrastructure.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Profile
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Accounts to develop against, one of each kind the platform has.
 *
 * Nothing else creates a platform user. Registration makes a student and
 * requires a confirmed email; an instructor can only be made by an
 * administrator through the directory; and the one account the schema seeds is
 * a super admin, which is the wrong shape for testing almost everything. So a
 * fresh database left you unable to sign in to the instructor workspace at all
 * until you had gone through the dashboard to make yourself an author.
 *
 * **Dev profile only, and that is the guarantee.** These are accounts with a
 * published password; a property could be set wrongly, a profile cannot be set
 * accidentally. Tests run under `test` and are unaffected: they build the
 * accounts they need, and seeded ones would turn up in directory listings that
 * assert on counts.
 *
 * Idempotent by email, and it never updates. If you change a password or
 * suspend one of these while working, that is a deliberate act and restarting
 * should not undo it.
 */
@Component
@Profile("dev")
class DevAccountSeeder(
    private val users: UserRepository,
    private val profiles: UserProfileRepository,
    private val adminUsers: AdminUserRepository,
    private val adminUserRoles: AdminUserRoleRepository,
    private val roles: RoleRepository,
    private val permissions: PermissionRepository,
    private val rolePermissions: RolePermissionRepository,
    private val passwordEncoder: PasswordEncoder,
    private val properties: SeedProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * After [ReferenceDataSeeder], because the staff account below needs a role
     * built out of the permission catalogue that one writes. Both listen for
     * the same event, so the order is stated rather than left to bean creation.
     */
    @EventListener(ApplicationReadyEvent::class)
    @Order(20)
    @Transactional
    fun seed() {
        if (!properties.devAccounts) {
            log.info("Development accounts are disabled")
            return
        }

        val created = mutableListOf<String>()

        INSTRUCTORS.forEach { person ->
            addUser(person, created) { Instructor(person.email, person.username, hash(), person.status) }
        }
        LEARNERS.forEach { person ->
            addUser(person, created) { Student(person.email, person.username, hash(), person.status) }
        }
        addStaffAdmin(created)

        if (created.isEmpty()) return

        // Loud, and with the password in it. A development account whose
        // password nobody knows is not a convenience, and one nobody notices
        // was created is a security surprise waiting for the day this
        // configuration reaches somewhere it should not.
        log.warn(
            "Seeded {} development account(s) with the password '{}': {}",
            created.size,
            PASSWORD,
            created.joinToString(", "),
        )
    }

    private fun addUser(person: Person, created: MutableList<String>, build: () -> User) {
        if (users.existsByEmailIgnoreCase(person.email)) return
        val user = users.save(build())
        profiles.save(UserProfile(user = user, firstName = person.firstName, lastName = person.lastName))
        created += "${person.email} (${person.label})"
    }

    /**
     * An administrator who is not a super admin.
     *
     * The seeded super admin holds every permission, so it can never fail a
     * permission check and is useless for telling whether one works. This one
     * holds a few, which is what an ordinary member of staff looks like.
     */
    private fun addStaffAdmin(created: MutableList<String>) {
        if (adminUsers.existsByEmailIgnoreCase(STAFF_EMAIL)) return

        val role = roles.findBySlug(STAFF_ROLE_SLUG).orElseGet {
            roles.save(
                Role(
                    name = "Course Editor",
                    slug = STAFF_ROLE_SLUG,
                    description = "Development role: the catalogue, and the people in it, but nothing destructive",
                    isSuper = false,
                    isSystem = false,
                ),
            )
        }
        val roleId = requireNotNull(role.id)
        val held = rolePermissions.permissionCodesOf(roleId).toSet()
        STAFF_PERMISSIONS
            .filterNot { it in held }
            // Only codes that exist, for the reason the reference seeder gives:
            // a role claiming a permission nothing enforces advertises an
            // authority it does not confer.
            .filter { permissions.existsById(it) }
            .forEach { rolePermissions.save(RolePermission(RolePermissionId(roleId, it))) }

        val admin = adminUsers.save(AdminUser(email = STAFF_EMAIL, username = "staff", passwordHash = hash()))
        adminUserRoles.save(AdminUserRole(AdminUserRoleId(requireNotNull(admin.id), roleId)))
        created += "$STAFF_EMAIL (staff admin, Course Editor)"
    }

    private fun hash(): String = requireNotNull(passwordEncoder.encode(PASSWORD))

    private data class Person(
        val email: String,
        val username: String,
        val firstName: String,
        val lastName: String,
        val label: String,
        val status: UserStatus = UserStatus.ACTIVE,
    )

    private companion object {
        /** Long enough to satisfy the twelve-character rule the API enforces. */
        const val PASSWORD = "Lernova-dev-2026"

        const val STAFF_EMAIL = "staff@elearning.local"
        const val STAFF_ROLE_SLUG = "course-editor"
        val STAFF_PERMISSIONS = listOf("course.read", "course.write", "course.publish", "user.read")

        /** Two, so co-instructors and "not your course" are both reachable. */
        val INSTRUCTORS = listOf(
            Person("instructor@elearning.local", "instructor", "Amira", "Ben Salah", "instructor"),
            Person("instructor2@elearning.local", "instructor2", "Karim", "Mansouri", "second instructor"),
        )

        /**
         * Two active and one pending: an account that has not confirmed its
         * address is refused at sign-in, and that path is easier to believe
         * when there is one sitting there to try it with.
         */
        val LEARNERS = listOf(
            Person("student@elearning.local", "student", "Nadia", "Cherif", "student"),
            Person("student2@elearning.local", "student2", "Youssef", "Trabelsi", "second student"),
            Person(
                "student.pending@elearning.local",
                "studentpending",
                "Sami",
                "Gharbi",
                "student, email unconfirmed",
                UserStatus.PENDING,
            ),
        )
    }
}
