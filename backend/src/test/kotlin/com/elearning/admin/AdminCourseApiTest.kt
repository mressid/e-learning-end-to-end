package com.elearning.admin

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * The dashboard's course administration, and - the point of it - that the four
 * course permissions stay separable rather than collapsing into one.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminCourseApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun superToken(): String = objectMapper.readTree(
        mockMvc.post("/api/v1/admin/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"admin@elearning.local","password":"change this password now"}"""
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
    ).get("accessToken").asString()

    private fun adminWith(vararg permissions: String): String {
        val su = superToken()
        val unique = "scoped-${System.nanoTime()}"
        val roleId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/roles") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("name" to "Role $unique", "permissions" to permissions.toList()),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val adminId = objectMapper.readTree(
            mockMvc.post("/api/v1/admin/admins") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $su")
                content = objectMapper.writeValueAsString(
                    mapOf("email" to "$unique@example.com", "username" to unique, "password" to password),
                )
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        mockMvc.post("/api/v1/admin/admins/$adminId/roles/$roleId") {
            header("Authorization", "Bearer $su")
        }.andExpect { status { isNoContent() } }
        return objectMapper.readTree(
            mockMvc.post("/api/v1/admin/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(
                    mapOf("email" to "$unique@example.com", "password" to password),
                )
            }.andExpect { status { isOk() } }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    private fun teacher(): String {
        val unique = "teacher-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        return objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    /** A draft course with one section and one item, owned by someone else. */
    private fun draft(owner: String, title: String = "Draft ${System.nanoTime()}"): Triple<String, String, String> {
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = objectMapper.writeValueAsString(mapOf("title" to title))
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val sectionId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"Section"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        val itemId = objectMapper.readTree(
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"Lesson","type":"LESSON"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        return Triple(courseId, sectionId, itemId)
    }

    // ---- listing ---------------------------------------------------------

    @Test
    fun `the admin listing shows drafts, which discovery never does`() {
        val owner = teacher()
        val title = "Hidden Draft ${System.nanoTime()}"
        draft(owner, title)

        // Public discovery lists PUBLISHED only.
        val public = mockMvc.get("/api/v1/courses") { param("q", title) }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertThat(objectMapper.readTree(public).get("totalElements").asInt()).isEqualTo(0)

        val admin = mockMvc.get("/api/v1/admin/courses") {
            header("Authorization", "Bearer ${superToken()}")
            param("q", title)
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val row = objectMapper.readTree(admin).get("content").get(0)
        assertThat(row.get("status").asString()).isEqualTo("DRAFT")
        // The owner is resolved for the page, so the dashboard can show a name.
        assertThat(row.get("ownerEmail").asString()).contains("@")
    }

    @Test
    fun `listing can be filtered by status`() {
        mockMvc.get("/api/v1/admin/courses") {
            header("Authorization", "Bearer ${superToken()}")
            param("status", "ARCHIVED")
        }.andExpect { status { isOk() } }
        mockMvc.get("/api/v1/admin/courses") {
            header("Authorization", "Bearer ${superToken()}")
            param("status", "NONSENSE")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INVALID_STATUS") }
        }
    }

    @Test
    fun `status counts are reported for the overview tiles`() {
        val body = mockMvc.get("/api/v1/admin/courses/stats") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        val json = objectMapper.readTree(body)
        assertThat(json.get("DRAFT")).isNotNull()
        assertThat(json.get("PUBLISHED")).isNotNull()
        assertThat(json.get("ARCHIVED")).isNotNull()
    }

    @Test
    fun `the catalogue needs course_read`() {
        mockMvc.get("/api/v1/admin/courses") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    // ---- the permissions stay separable ---------------------------------

    @Test
    fun `course_read sees a draft it cannot touch`() {
        val owner = teacher()
        val (courseId, _, _) = draft(owner)
        val reader = adminWith("course.read")

        // A draft 404s to a stranger; course.read is what turns that into a view.
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $reader")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DRAFT") }
        }

        // Seeing is not editing.
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $reader")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `course_publish publishes without conferring the right to rewrite`() {
        val owner = teacher()
        val (courseId, _, itemId) = draft(owner)
        val publisher = adminWith("course.read", "course.publish")

        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $publisher")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PUBLISHED") }
        }

        // Publishing a finished course is not authoring it: someone who works a
        // review queue should not thereby be able to rewrite the content.
        mockMvc.delete("/api/v1/items/$itemId") {
            header("Authorization", "Bearer $publisher")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `course_delete prunes structure without conferring publish`() {
        val owner = teacher()
        val (courseId, _, itemId) = draft(owner)
        val pruner = adminWith("course.read", "course.delete")

        mockMvc.delete("/api/v1/items/$itemId") {
            header("Authorization", "Bearer $pruner")
        }.andExpect { status { isNoContent() } }

        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $pruner")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `the owner still governs their own course without any permission`() {
        val owner = teacher()
        val (courseId, _, _) = draft(owner)
        // The relationship is checked first and still decides; permissions are
        // a fallback for people who have no relationship to the course.
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `an admin with no course permissions is still a stranger to a draft`() {
        val owner = teacher()
        val (courseId, _, _) = draft(owner)
        // 404 rather than 403: whether an unpublished course exists is not
        // public information, and holding an admin account does not change that.
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect { status { isNotFound() } }
    }
}
