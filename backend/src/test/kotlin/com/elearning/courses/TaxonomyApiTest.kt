package com.elearning.courses

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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/**
 * Categories and tags: curated reference data on one side, free-form keywords
 * on the other, and the different authority each implies.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaxonomyApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val accounts: TestAccounts,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private fun tokenFor(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("accessToken").asString()
    }

    /**
     * Somebody who can own a course.
     *
     * Registration produces a student, and a student cannot author, so an
     * instructor account is made directly. [tokenFor] stays the learner.
     */
    private fun instructorTokenFor(label: String): String {
        val unique = "$label-${System.nanoTime()}"
        accounts.instructor("$unique@example.com", unique, password)
        return objectMapper.readTree(
            mockMvc.post("/api/v1/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$unique@example.com","password":"$password"}"""
            }.andReturn().response.contentAsString,
        ).get("accessToken").asString()
    }

    private fun createCourse(token: String): String {
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Taxonomy Course ${System.nanoTime()}"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    private fun categoryIdBySlug(slug: String): String {
        val body = mockMvc.get("/api/v1/categories").andReturn().response.contentAsString
        return objectMapper.readTree(body).first { it.get("slug").asString() == slug }
            .get("id").asString()
    }

    @Test
    fun `the seeded categories are browsable without an account`() {
        val body = mockMvc.get("/api/v1/categories")
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString

        val tree = objectMapper.readTree(body)
        assertThat(tree.size()).isGreaterThan(20)

        // The seed's second level hangs off the first by slug lookup, so a
        // child with no parent would mean the join silently missed.
        val development = tree.first { it.get("slug").asString() == "development" }
        // The API is configured non_null platform-wide, so a top-level category
        // omits the key rather than sending an explicit null.
        assertThat(development.get("parentId")).isNull()
        val web = tree.first { it.get("slug").asString() == "web-development" }
        assertThat(web.get("parentId").asString()).isEqualTo(development.get("id").asString())
    }

    @Test
    fun `there is no way to create a category`() {
        val token = tokenFor("editor")
        // Curated reference data: V1 has no admin role, so rather than invent
        // one the write side simply does not exist.
        mockMvc.post("/api/v1/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"name":"Invented","slug":"invented"}"""
        }.andExpect { status { isMethodNotAllowed() } }
    }

    @Test
    fun `an editor sets categories and they come back on the course`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        val design = categoryIdBySlug("design")
        val ml = categoryIdBySlug("machine-learning")

        mockMvc.put("/api/v1/courses/$courseId/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"categoryIds":["$design","$ml"]}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.categories.length()") { value(2) }
        }
    }

    @Test
    fun `setting categories replaces the set rather than adding to it`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        val design = categoryIdBySlug("design")
        val ml = categoryIdBySlug("machine-learning")

        fun set(json: String) = mockMvc.put("/api/v1/courses/$courseId/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = json
        }.andExpect { status { isOk() } }

        set("""{"categoryIds":["$design","$ml"]}""")
        set("""{"categoryIds":["$design"]}""")

        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            jsonPath("$.categories.length()") { value(1) }
            jsonPath("$.categories[0].slug") { value("design") }
        }

        // An empty list has to be expressible, or the last category can never
        // be removed - which is why this is a PUT and not add/remove routes.
        set("""{"categoryIds":[]}""")
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect { jsonPath("$.categories.length()") { value(0) } }
    }

    @Test
    fun `an unknown category is reported, not silently dropped`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        mockMvc.put("/api/v1/courses/$courseId/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"categoryIds":["00000000-0000-0000-0000-000000000000"]}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("CATEGORY_NOT_FOUND") }
        }
    }

    @Test
    fun `a stranger cannot retag someone else's course`() {
        val owner = instructorTokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)
        mockMvc.put("/api/v1/courses/$courseId/tags") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $stranger")
            content = """{"tags":["hijacked"]}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `tags differing only in case or punctuation are one tag`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        val unique = "Kotlin Coroutines ${System.nanoTime()}"

        val body = mockMvc.put("/api/v1/courses/$courseId/tags") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = objectMapper.writeValueAsString(
                mapOf("tags" to listOf(unique, unique.uppercase(), unique.replace(" ", "-"))),
            )
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        // Slug is the identity, so three spellings collapse to one tag rather
        // than three near-duplicates no filter could group.
        assertThat(objectMapper.readTree(body).size()).isEqualTo(1)
    }

    @Test
    fun `a tag created on one course is reused by the next, not duplicated`() {
        val owner = instructorTokenFor("owner")
        val other = instructorTokenFor("other")
        val name = "Shared Topic ${System.nanoTime()}"

        fun tag(token: String, courseId: String) = mockMvc.put("/api/v1/courses/$courseId/tags") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(mapOf("tags" to listOf(name)))
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString

        val first = objectMapper.readTree(tag(owner, createCourse(owner))).first().get("id").asString()
        val second = objectMapper.readTree(tag(other, createCourse(other))).first().get("id").asString()
        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `refuses an unreasonable number of tags`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        val many = (1..40).map { "tag-number-$it" }
        mockMvc.put("/api/v1/courses/$courseId/tags") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = objectMapper.writeValueAsString(mapOf("tags" to many))
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `the course listing carries taxonomy for every row`() {
        val owner = instructorTokenFor("owner")
        val courseId = createCourse(owner)
        val design = categoryIdBySlug("design")
        mockMvc.put("/api/v1/courses/$courseId/categories") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"categoryIds":["$design"]}"""
        }.andExpect { status { isOk() } }

        // A section and an item, so the course can be published into discovery.
        val sectionBody = mockMvc.post("/api/v1/courses/$courseId/sections") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Section"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val sectionId = objectMapper.readTree(sectionBody).get("id").asString()
        mockMvc.post("/api/v1/sections/$sectionId/items") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Lesson","type":"LESSON"}"""
        }.andExpect { status { isCreated() } }
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }

        val listing = mockMvc.get("/api/v1/courses") { param("size", "100") }
            .andExpect { status { isOk() } }
            .andReturn().response.contentAsString
        val row = objectMapper.readTree(listing).get("content")
            .first { it.get("id").asString() == courseId }
        assertThat(row.get("categories").first().get("slug").asString()).isEqualTo("design")
    }
}
