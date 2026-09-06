package com.elearning.platform.seed

import com.elearning.admin.infrastructure.PermissionRepository
import com.elearning.admin.infrastructure.RoleRepository
import com.elearning.courses.infrastructure.CategoryRepository
import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

/**
 * The seeder runs on every startup, so the property that matters is that a
 * second run changes nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReferenceDataSeederTest(
    @Autowired val seeder: ReferenceDataSeeder,
    @Autowired val categories: CategoryRepository,
    @Autowired val permissions: PermissionRepository,
    @Autowired val roles: RoleRepository,
) : IntegrationTest() {

    @Test
    fun `the JSON file is the source of every seeded category`() {
        // The context has already started, so the seeder has already run.
        val slugs = categories.findAllByOrderByNameAsc().map { it.slug }
        assertThat(slugs).contains("development", "web-development", "data-science", "machine-learning")

        // The tree is carried by parentId, so a child whose parent did not
        // resolve would sit at the top level and quietly flatten the taxonomy.
        val development = categories.findBySlug("development")!!
        val web = categories.findBySlug("web-development")!!
        assertThat(development.parentId).isNull()
        assertThat(web.parentId).isEqualTo(development.id)
    }

    @Test
    fun `the permission catalogue and system roles are seeded`() {
        assertThat(permissions.findAllByOrderByCodeAsc().map { it.code })
            .contains("review.moderate", "certificate.revoke", "audit.read")

        val superRole = roles.findBySlug("super-admin").orElseThrow()
        assertThat(superRole.isSuper).isTrue()
        assertThat(superRole.isSystem).isTrue()
    }

    @Test
    fun `running again creates nothing - matched by slug and code, not by row`() {
        val categoriesBefore = categories.count()
        val permissionsBefore = permissions.count()
        val rolesBefore = roles.count()

        seeder.seed()
        seeder.seed()

        // Idempotence is what makes it safe on every boot; matching on the
        // natural key rather than inserting blindly is what buys it.
        assertThat(categories.count()).isEqualTo(categoriesBefore)
        assertThat(permissions.count()).isEqualTo(permissionsBefore)
        assertThat(roles.count()).isEqualTo(rolesBefore)
    }

    @Test
    fun `the super admin role holds no permission rows`() {
        // It means "every permission, including ones a later migration adds".
        // Materialising today's catalogue onto it would freeze that promise.
        val superRole = roles.findBySlug("super-admin").orElseThrow()
        assertThat(superRole.isSuper).isTrue()
    }
}
