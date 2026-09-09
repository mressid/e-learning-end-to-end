package com.elearning.admin

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.TestAccounts
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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/**
 * The taxonomy tree and the asset library - the two places the dashboard
 * removes things, and so the two that need to refuse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminLibraryApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val accounts: TestAccounts,
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

    private fun learner(label: String): String {
        val unique = "$label-${System.nanoTime()}"
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

    /** An instructor: registration makes students, and a student cannot author. */
    private fun teacher(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        accounts.instructor("$unique@example.com", unique, password)
        return objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    private fun createCategory(token: String, name: String, parentId: String? = null): String {
        val payload = mutableMapOf<String, Any?>("name" to name)
        if (parentId != null) payload["parentId"] = parentId
        val body = mockMvc.post("/api/v1/admin/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(payload)
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    // ---- the taxonomy tree ------------------------------------------------

    @Test
    fun `an administrator can now extend the tree that seeding started`() {
        val token = adminWith("category.manage")
        val name = "Robotics ${System.nanoTime()}"
        val id = createCategory(token, name)

        // Public listing picks it up: the tree is one thing, however it got there.
        val body = mockMvc.get("/api/v1/categories").andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        assertThat(body).contains(id)
    }

    @Test
    fun `extending the tree needs category_manage`() {
        // Course editors attach what exists; deciding what exists is a platform
        // authority, and until it existed there was deliberately no endpoint.
        mockMvc.post("/api/v1/admin/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${adminWith("audit.read")}")
            content = """{"name":"Sneaky ${System.nanoTime()}"}"""
        }.andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }

        mockMvc.post("/api/v1/admin/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${learner("editor")}")
            content = """{"name":"Sneaky ${System.nanoTime()}"}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `renaming leaves the slug alone`() {
        val token = adminWith("category.manage")
        val id = createCategory(token, "Original Name ${System.nanoTime()}")
        val before = objectMapper.readTree(
            mockMvc.get("/api/v1/categories").andReturn().response.contentAsString,
        ).first { it.get("id").asString() == id }.get("slug").asString()

        mockMvc.patch("/api/v1/admin/categories/$id") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"name":"Renamed Entirely"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Renamed Entirely") }
            // The slug is what the seeder matches on and what a filter URL
            // carries; regenerating it would orphan one and break the other.
            jsonPath("$.slug") { value(before) }
        }
    }

    @Test
    fun `a category with children cannot be deleted`() {
        val token = adminWith("category.manage")
        val parent = createCategory(token, "Parent ${System.nanoTime()}")
        createCategory(token, "Child ${System.nanoTime()}", parent)

        // parent_id cascades, so the database would take the children silently.
        mockMvc.delete("/api/v1/admin/categories/$parent") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("CATEGORY_HAS_CHILDREN") }
        }
    }

    @Test
    fun `a category still used by a course cannot be deleted`() {
        val token = adminWith("category.manage")
        val categoryId = createCategory(token, "In Use ${System.nanoTime()}")
        val teacher = teacher("teacher")
        val courseId = objectMapper.readTree(
            mockMvc.post("/api/v1/courses") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $teacher")
                content = """{"title":"Categorised ${System.nanoTime()}"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
        ).get("id").asString()
        mockMvc.put("/api/v1/courses/$courseId/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $teacher")
            content = """{"categoryIds":["$categoryId"]}"""
        }.andExpect { status { isOk() } }

        // course_categories cascades too, so this would strip the course's
        // categorisation without saying so.
        mockMvc.delete("/api/v1/admin/categories/$categoryId") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("CATEGORY_IN_USE") }
        }
    }

    @Test
    fun `an unused leaf category is deleted`() {
        val token = adminWith("category.manage")
        val id = createCategory(token, "Disposable ${System.nanoTime()}")
        mockMvc.delete("/api/v1/admin/categories/$id") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }
    }

    // ---- the asset library ------------------------------------------------

    @Test
    fun `the library lists uploads with their uploader resolved`() {
        mockMvc.get("/api/v1/admin/media") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.content") { exists() }
        }
    }

    @Test
    fun `the library never exposes object keys`() {
        val body = mockMvc.get("/api/v1/admin/media") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        // Keys are generated precisely so a caller cannot address storage
        // directly; publishing them would undo that.
        assertThat(body).doesNotContain("objectKey")
    }

    @Test
    fun `browsing needs media_read and deleting needs media_delete`() {
        mockMvc.get("/api/v1/admin/media") {
            header("Authorization", "Bearer ${adminWith("audit.read")}")
        }.andExpect { status { isForbidden() } }

        mockMvc.delete("/api/v1/admin/media/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer ${adminWith("media.read")}")
        }.andExpect {
            // Reading the library does not imply pruning it.
            status { isForbidden() }
            jsonPath("$.code") { value("PERMISSION_DENIED") }
        }
    }

    @Test
    fun `deleting a file that does not exist is a 404`() {
        mockMvc.delete("/api/v1/admin/media/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer ${superToken()}")
        }.andExpect { status { isNotFound() } }
    }
}
