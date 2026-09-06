package com.elearning.platform.seed

import com.elearning.admin.domain.Permission
import com.elearning.admin.domain.Role
import com.elearning.admin.domain.RolePermission
import com.elearning.admin.domain.RolePermissionId
import com.elearning.admin.infrastructure.PermissionRepository
import com.elearning.admin.infrastructure.RolePermissionRepository
import com.elearning.admin.infrastructure.RoleRepository
import com.elearning.courses.domain.Category
import com.elearning.courses.infrastructure.CategoryRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper

/**
 * Seeds the platform's reference data from `seed/reference-data.json`.
 *
 * One place for every seed, and one file to edit. Previously the categories
 * lived in V3 and the permission catalogue in V6, which meant adding a category
 * required writing a migration and re-deploying the schema for what is really
 * content.
 *
 * Three rules make this safe to run on every startup:
 *
 * 1. **Idempotent.** Matched by natural key - category and role by slug,
 *    permission by code - so a second run updates rather than duplicates.
 * 2. **Never deletes.** Removing an entry from the JSON leaves the row alone. A
 *    category may already be attached to courses and a permission already held
 *    by a role, and both cascade: deleting on absence would silently strip
 *    live data because somebody tidied a file.
 * 3. **Additive to the migrations.** V3 and V6 already inserted this same data
 *    and Flyway migrations are immutable history, so they cannot be emptied
 *    retroactively. The seeder finds those rows and leaves them as they are;
 *    from here on the JSON is the source of truth and the SQL is just how the
 *    first copy arrived.
 *
 * Runs on `ApplicationReadyEvent`, which is after Flyway - the tables have to
 * exist before anything can be written into them.
 */
@Component
class ReferenceDataSeeder(
    private val categories: CategoryRepository,
    private val permissions: PermissionRepository,
    private val roles: RoleRepository,
    private val rolePermissions: RolePermissionRepository,
    private val resourceLoader: ResourceLoader,
    private val objectMapper: ObjectMapper,
    private val properties: SeedProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun seed() {
        if (!properties.enabled) {
            log.info("Reference-data seeding is disabled")
            return
        }

        val resource = resourceLoader.getResource(properties.location)
        if (!resource.exists()) {
            // Loud rather than silent: an empty category table makes the whole
            // taxonomy API unusable, and a missing permission catalogue makes
            // every role unbuildable.
            log.error("Seed file {} not found; reference data was not seeded", properties.location)
            return
        }

        val data = resource.inputStream.use { objectMapper.readValue(it, ReferenceData::class.java) }
        val counts = Counts()
        seedCategories(data.categories, counts)
        seedPermissions(data.permissions, counts)
        seedRoles(data.roles, counts)

        if (counts.created > 0 || counts.updated > 0) {
            log.info("Reference data seeded: {} created, {} updated", counts.created, counts.updated)
        }
    }

    private fun seedCategories(entries: List<CategorySeed>, counts: Counts) {
        entries.forEach { top ->
            val parent = upsertCategory(top.slug, top.name, parentId = null, counts = counts)
            top.children.forEach { child ->
                upsertCategory(child.slug, child.name, parentId = parent.id, counts = counts)
            }
        }
    }

    private fun upsertCategory(slug: String, name: String, parentId: java.util.UUID?, counts: Counts): Category {
        val existing = categories.findBySlug(slug)
        if (existing != null) {
            // The slug is the identity, so a renamed entry updates in place
            // rather than appearing alongside the old one.
            if (existing.name != name || existing.parentId != parentId) {
                existing.name = name
                existing.parentId = parentId
                counts.updated++
            }
            return existing
        }
        counts.created++
        return categories.save(Category(parentId = parentId, name = name, slug = slug))
    }

    private fun seedPermissions(entries: List<PermissionSeed>, counts: Counts) {
        entries.forEach { entry ->
            val existing = permissions.findById(entry.code).orElse(null)
            if (existing == null) {
                permissions.save(Permission(code = entry.code, description = entry.description))
                counts.created++
            } else if (existing.description != entry.description) {
                existing.description = entry.description
                counts.updated++
            }
        }
    }

    private fun seedRoles(entries: List<RoleSeed>, counts: Counts) {
        entries.forEach { entry ->
            val role = roles.findBySlug(entry.slug).orElse(null)
                ?: roles.save(
                    Role(
                        name = entry.name,
                        slug = entry.slug,
                        description = entry.description,
                        isSuper = entry.isSuper,
                        isSystem = entry.isSystem,
                    ),
                ).also { counts.created++ }

            // A super role means "every permission, including future ones", so
            // it holds no rows: writing a fixed list would freeze it at the
            // catalogue as it stands today.
            if (role.isSuper || entry.permissions.isEmpty()) return@forEach

            val roleId = requireNotNull(role.id)
            val held = rolePermissions.permissionCodesOf(roleId).toSet()
            entry.permissions
                .filterNot { it in held }
                // Only codes that exist: a role claiming a permission nothing
                // enforces would advertise an authority it does not confer.
                .filter { permissions.existsById(it) }
                .forEach {
                    rolePermissions.save(RolePermission(RolePermissionId(roleId, it)))
                    counts.created++
                }
        }
    }

    private class Counts(var created: Int = 0, var updated: Int = 0)
}

data class ReferenceData(
    val categories: List<CategorySeed> = emptyList(),
    val permissions: List<PermissionSeed> = emptyList(),
    val roles: List<RoleSeed> = emptyList(),
)

data class CategorySeed(
    val slug: String = "",
    val name: String = "",
    val children: List<CategorySeed> = emptyList(),
)

data class PermissionSeed(val code: String = "", val description: String = "")

data class RoleSeed(
    val slug: String = "",
    val name: String = "",
    val description: String? = null,
    val isSuper: Boolean = false,
    val isSystem: Boolean = false,
    val permissions: List<String> = emptyList(),
)
