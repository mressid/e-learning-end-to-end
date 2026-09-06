package com.elearning.courses

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/**
 * Course authoring, publication and - most importantly - who is allowed to do
 * what to which course (§11).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
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

    private fun createCourse(token: String, title: String = "Test Course ${System.nanoTime()}"): String {
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"$title","description":"a description"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    private fun addItem(token: String, courseId: String) {
        val sectionBody = mockMvc.post("/api/v1/courses/$courseId/sections") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Section"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val sectionId = objectMapper.readTree(sectionBody).get("id").asString()

        mockMvc.post("/api/v1/sections/$sectionId/items") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Lesson","type":"LESSON"}"""
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `creates a course as a draft owned by the caller`() {
        val token = tokenFor("owner")
        mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Programmation Avancée","level":"ADVANCED"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("DRAFT") }
            // Accents are stripped so the slug is URL-safe.
            jsonPath("$.slug") { value("programmation-avancee") }
        }
    }

    @Test
    fun `anonymous callers cannot create a course`() {
        mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"title":"Nope"}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `a draft is hidden from other users as a 404, not a 403`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)

        // 403 would confirm the course exists; 404 reveals nothing.
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $stranger")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COURSE_NOT_FOUND") }
        }

        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a stranger cannot modify someone else's course`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)

        mockMvc.patch("/api/v1/courses/$courseId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $stranger")
            content = """{"title":"Hijacked"}"""
        }.andExpect {
            // Not 404: publication state is not what is being hidden here.
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_ACCESS_DENIED") }
        }
    }

    @Test
    fun `an empty course cannot be published`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)

        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("COURSE_NOT_PUBLISHABLE") }
        }
    }

    @Test
    fun `publishing makes a course anonymously visible`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        addItem(owner, courseId)

        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PUBLISHED") }
            jsonPath("$.publishedAt") { exists() }
        }

        mockMvc.get("/api/v1/courses/$courseId").andExpect { status { isOk() } }
    }

    @Test
    fun `sections and items keep one ordered sequence`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)

        val sectionBody = mockMvc.post("/api/v1/courses/$courseId/sections") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Basics"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.position") { value(0) }
        }.andReturn().response.contentAsString
        val sectionId = objectMapper.readTree(sectionBody).get("id").asString()

        listOf("LESSON", "QUIZ", "ASSIGNMENT").forEachIndexed { index, type ->
            mockMvc.post("/api/v1/sections/$sectionId/items") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"Item $index","type":"$type"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.position") { value(index) }
            }
        }

        mockMvc.get("/api/v1/sections/$sectionId/items") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(3) }
            jsonPath("$[0].type") { value("LESSON") }
            jsonPath("$[2].type") { value("ASSIGNMENT") }
        }
    }

    @Test
    fun `full-text search finds a course by a word in its description`() {
        val owner = tokenFor("owner")
        val marker = "quokka${System.nanoTime()}"
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Searchable ${System.nanoTime()}","description":"about $marker things"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val courseId = objectMapper.readTree(body).get("id").asString()
        addItem(owner, courseId)
        mockMvc.post("/api/v1/courses/$courseId/publish") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() } }

        mockMvc.get("/api/v1/courses?q=$marker").andExpect {
            status { isOk() }
            jsonPath("$.totalElements") { value(1) }
            jsonPath("$.content[0].id") { value(courseId) }
        }
    }

    @Test
    fun `search input is treated as literal text, not query syntax`() {
        // plainto_tsquery must neutralise operators; a raw to_tsquery would error.
        mockMvc.get("/api/v1/courses") { param("q", "python & !( invalid") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `unpublished courses never appear in the public listing`() {
        val owner = tokenFor("owner")
        createCourse(owner, "Hidden Draft ${System.nanoTime()}")

        val body = mockMvc.get("/api/v1/courses?size=100")
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        // Index explicitly: Jackson 3's JsonNode declares its own map(), which
        // shadows Kotlin's Iterable.map and would hand the lambda the array node
        // itself rather than each element.
        val content = objectMapper.readTree(body).get("content")
        val statuses = (0 until content.size()).map { content.get(it).get("status").asString() }
        assertThat(statuses).doesNotContain("DRAFT", "ARCHIVED")
    }

    @Test
    fun `rejects an out-of-range page size with a field error`() {
        mockMvc.get("/api/v1/courses?size=500").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("size") }
        }
    }

    // ---- thumbnails and reordering ---------------------------------------

    /** Uploads a real image-ish object to the public bucket and returns its id. */
    private fun uploadPublic(token: String): String {
        val ticket = mockMvc.post("/api/v1/media/uploads") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"filename":"cover.png","contentType":"text/plain","visibility":"PUBLIC"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val node = objectMapper.readTree(ticket)
        val mediaId = node.get("mediaId").asString()
        (java.net.URI.create(node.get("uploadUrl").asString()).toURL()
            .openConnection() as java.net.HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            outputStream.use { it.write("image-bytes".toByteArray()) }
            check(responseCode == 200) { "upload failed: $responseCode" }
            disconnect()
        }
        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        return mediaId
    }

    @Test
    fun `a public upload becomes a stable thumbnail url`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        val mediaId = uploadPublic(owner)

        val body = mockMvc.post("/api/v1/courses/$courseId/thumbnail") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"mediaId":"$mediaId"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.thumbnailUrl") { exists() }
        }.andReturn().response.contentAsString

        // Stable, not presigned: no expiry query string to invalidate a cache.
        val url = objectMapper.readTree(body).get("thumbnailUrl").asString()
        assertThat(url).doesNotContain("X-Amz-Signature")
    }

    @Test
    fun `a private upload cannot be used as a thumbnail`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        // Default visibility is PRIVATE, so its URL would expire.
        val ticket = mockMvc.post("/api/v1/media/uploads") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"filename":"cover.png","contentType":"text/plain"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val node = objectMapper.readTree(ticket)
        (java.net.URI.create(node.get("uploadUrl").asString()).toURL()
            .openConnection() as java.net.HttpURLConnection).run {
            requestMethod = "PUT"
            doOutput = true
            setRequestProperty("Content-Type", "text/plain")
            outputStream.use { it.write("x".toByteArray()) }
            check(responseCode == 200)
            disconnect()
        }
        val mediaId = node.get("mediaId").asString()
        mockMvc.post("/api/v1/media/$mediaId/complete") { header("Authorization", "Bearer $owner") }
            .andExpect { status { isOk() } }

        mockMvc.post("/api/v1/courses/$courseId/thumbnail") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"mediaId":"$mediaId"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("THUMBNAIL_MUST_BE_PUBLIC") }
        }
    }

    @Test
    fun `sections can be reordered`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        val ids = listOf("A", "B", "C").map { title ->
            val body = mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"$title"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
            objectMapper.readTree(body).get("id").asString()
        }

        val reversed = ids.reversed()
        mockMvc.put("/api/v1/courses/$courseId/sections/order") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"orderedIds":[${reversed.joinToString(",") { "\"$it\"" }}]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$[0].title") { value("C") }
            jsonPath("$[2].title") { value("A") }
        }

        mockMvc.get("/api/v1/courses/$courseId/sections") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            jsonPath("$[0].title") { value("C") }
            jsonPath("$[0].position") { value(0) }
            jsonPath("$[2].title") { value("A") }
        }
    }

    @Test
    fun `a partial or duplicated order is rejected`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        val ids = listOf("A", "B").map { title ->
            val body = mockMvc.post("/api/v1/courses/$courseId/sections") {
                contentType = MediaType.APPLICATION_JSON
                header("Authorization", "Bearer $owner")
                content = """{"title":"$title"}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
            objectMapper.readTree(body).get("id").asString()
        }

        // Missing one: the rest would silently keep stale positions.
        mockMvc.put("/api/v1/courses/$courseId/sections/order") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"orderedIds":["${ids[0]}"]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("INCOMPLETE_ORDER") }
        }

        mockMvc.put("/api/v1/courses/$courseId/sections/order") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"orderedIds":["${ids[0]}","${ids[0]}"]}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("DUPLICATE_IN_ORDER") }
        }
    }

    @Test
    fun `a stranger cannot reorder someone else's course`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)
        mockMvc.put("/api/v1/courses/$courseId/sections/order") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $stranger")
            content = """{"orderedIds":[]}"""
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `rejects a malformed uuid with 400 rather than 500`() {
        mockMvc.get("/api/v1/courses/not-a-uuid").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_PARAMETER") }
        }
    }

    // ---- archiving -------------------------------------------------------

    @Test
    fun `archiving retires a published course and drops it out of discovery`() {
        val owner = tokenFor("owner")
        val title = "Retired Course ${System.nanoTime()}"
        val courseId = createCourse(owner, title)
        addItem(owner, courseId)
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/courses/$courseId/archive") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ARCHIVED") }
        }

        // Discovery lists PUBLISHED only, so the row leaves the catalogue
        // without any extra filtering at the query site.
        val listing = mockMvc.get("/api/v1/courses") { param("q", title) }
            .andReturn().response.contentAsString
        assertThat(listing).doesNotContain(courseId)
    }

    @Test
    fun `an archived course is invisible to strangers but still reachable by its owner`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)
        addItem(owner, courseId)
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/archive") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }

        // 404 rather than 403: an archived course is no longer public, and a
        // 403 would confirm it exists - the same rule drafts follow.
        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isNotFound() } }

        mockMvc.get("/api/v1/courses/$courseId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ARCHIVED") }
        }
    }

    @Test
    fun `an archived course cannot be published directly, only after a return to draft`() {
        val owner = tokenFor("owner")
        val courseId = createCourse(owner)
        addItem(owner, courseId)
        mockMvc.post("/api/v1/courses/$courseId/archive") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }

        // Restoring has to be deliberate: publishing straight from ARCHIVED is
        // refused, so a retired course cannot quietly reappear.
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("COURSE_NOT_PUBLISHABLE") }
        }

        mockMvc.post("/api/v1/courses/$courseId/unpublish") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("DRAFT") }
        }
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("PUBLISHED") }
        }
    }

    @Test
    fun `a stranger cannot archive someone else's course`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val courseId = createCourse(owner)
        mockMvc.post("/api/v1/courses/$courseId/archive") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }
}
