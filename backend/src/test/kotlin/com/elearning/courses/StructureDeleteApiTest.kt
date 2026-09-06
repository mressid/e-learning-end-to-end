package com.elearning.courses

import com.elearning.shared.testing.IntegrationTest
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
import org.springframework.test.web.servlet.put
import tools.jackson.databind.ObjectMapper

/**
 * Deleting structure, and the line where it stops being the editor's to delete.
 *
 * `course_items` cascades into lessons, quizzes and assignments, and onward into
 * quiz attempts, quiz responses, assignment submissions and learning progress.
 * Authoring material is the editor's to remove; a student's answers and marks
 * are not, and nothing here is recoverable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StructureDeleteApiTest(
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

    private fun course(token: String): Pair<String, String> {
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Deletable ${System.nanoTime()}"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val courseId = objectMapper.readTree(body).get("id").asString()
        val sectionBody = mockMvc.post("/api/v1/courses/$courseId/sections") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Section"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return courseId to objectMapper.readTree(sectionBody).get("id").asString()
    }

    private fun item(token: String, sectionId: String, title: String = "Lesson"): String {
        val body = mockMvc.post("/api/v1/sections/$sectionId/items") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"$title","type":"LESSON"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    private fun publishAndEnrol(owner: String, student: String, courseId: String) {
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `an untouched item is deleted`() {
        val owner = tokenFor("owner")
        val (_, sectionId) = course(owner)
        val itemId = item(owner, sectionId)

        mockMvc.delete("/api/v1/items/$itemId") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isNoContent() } }

        val listing = mockMvc.get("/api/v1/sections/$sectionId/items") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assert(!listing.contains(itemId)) { "deleted item still listed" }
    }

    @Test
    fun `deleting an item a student has worked on is refused`() {
        val owner = tokenFor("owner")
        val student = tokenFor("student")
        val (courseId, sectionId) = course(owner)
        val itemId = item(owner, sectionId)
        publishAndEnrol(owner, student, courseId)

        mockMvc.post("/api/v1/items/$itemId/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }

        // The cascade would take the progress row with it, silently rewriting
        // what the student had completed.
        mockMvc.delete("/api/v1/items/$itemId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ITEM_HAS_STUDENT_ACTIVITY") }
        }

        // Still there, and still the student's.
        mockMvc.get("/api/v1/courses/$courseId/progress") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `a section is deleted with its items`() {
        val owner = tokenFor("owner")
        val (courseId, sectionId) = course(owner)
        item(owner, sectionId, "One")
        item(owner, sectionId, "Two")

        mockMvc.delete("/api/v1/sections/$sectionId") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isNoContent() } }

        val sections = mockMvc.get("/api/v1/courses/$courseId/sections") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assert(!sections.contains(sectionId)) { "deleted section still listed" }
    }

    @Test
    fun `one worked-on item blocks the whole section, leaving nothing half-deleted`() {
        val owner = tokenFor("owner")
        val student = tokenFor("student")
        val (courseId, sectionId) = course(owner)
        val untouched = item(owner, sectionId, "Untouched")
        val worked = item(owner, sectionId, "Worked")
        publishAndEnrol(owner, student, courseId)

        mockMvc.post("/api/v1/items/$worked/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }

        mockMvc.delete("/api/v1/sections/$sectionId") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ITEM_HAS_STUDENT_ACTIVITY") }
        }

        // All or nothing: the untouched sibling must not have been taken either,
        // or the section would be left holding whichever items had submissions.
        val items = mockMvc.get("/api/v1/sections/$sectionId/items") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isOk() } }.andReturn().response.contentAsString
        assert(items.contains(untouched)) { "untouched sibling was removed by a refused delete" }
        assert(items.contains(worked)) { "worked item was removed by a refused delete" }
    }

    @Test
    fun `an attempted quiz blocks deletion just as progress does`() {
        val owner = tokenFor("owner")
        val student = tokenFor("student")
        val (courseId, sectionId) = course(owner)
        val quizItemBody = mockMvc.post("/api/v1/sections/$sectionId/items") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Quiz","type":"QUIZ"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val quizItem = objectMapper.readTree(quizItemBody).get("id").asString()

        mockMvc.put("/api/v1/items/$quizItem/quiz") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"title":"Check","passingScore":50}"""
        }.andExpect { status { isOk() } }
        mockMvc.post("/api/v1/items/$quizItem/quiz/questions") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $owner")
            content = """{"type":"SINGLE_CHOICE","text":"2 + 2?","points":10,
                "options":[{"text":"4","isCorrect":true},{"text":"5","isCorrect":false}]}"""
        }.andExpect { status { isCreated() } }

        publishAndEnrol(owner, student, courseId)
        mockMvc.post("/api/v1/items/$quizItem/quiz/attempts") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        // A different module's student data, reached through its own probe.
        mockMvc.delete("/api/v1/items/$quizItem") {
            header("Authorization", "Bearer $owner")
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ITEM_HAS_STUDENT_ACTIVITY") }
        }
    }

    @Test
    fun `a stranger cannot delete someone else's structure`() {
        val owner = tokenFor("owner")
        val stranger = tokenFor("stranger")
        val (_, sectionId) = course(owner)
        val itemId = item(owner, sectionId)

        mockMvc.delete("/api/v1/items/$itemId") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
        mockMvc.delete("/api/v1/sections/$sectionId") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `deleting something that is not there is a 404`() {
        val owner = tokenFor("owner")
        mockMvc.delete("/api/v1/items/00000000-0000-0000-0000-000000000000") {
            header("Authorization", "Bearer $owner")
        }.andExpect { status { isNotFound() } }
    }
}
