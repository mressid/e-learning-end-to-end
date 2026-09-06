package com.elearning

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * Verifies the context starts and that Flyway actually built the schema.
 * A context-load test that never touches the database would pass even if the
 * migrations were broken.
 */
@SpringBootTest
@ActiveProfiles("test")
class ELearningApplicationTests(
    @Autowired val jdbcTemplate: JdbcTemplate,
) : IntegrationTest() {

    @Test
    fun `context loads`() {
    }

    @Test
    fun `flyway applied the initial schema`() {
        val applied = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success = true",
            Int::class.java,
        )
        assertThat(applied).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `core tables exist`() {
        val tables = jdbcTemplate.queryForList(
            "select table_name from information_schema.tables where table_schema = 'public'",
            String::class.java,
        )
        assertThat(tables).contains("users", "courses", "course_items", "enrollments", "media_objects")
    }
}
