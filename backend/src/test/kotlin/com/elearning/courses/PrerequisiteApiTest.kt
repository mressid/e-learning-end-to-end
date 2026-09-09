package com.elearning.courses

import com.elearning.shared.testing.IntegrationTest
import com.elearning.shared.testing.TestAccounts
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
 * Prerequisites at both levels, which are deliberately different in kind.
 *
 * Between courses they are **free text and enforce nothing**: an enforced edge
 * had no safe behaviour once the required course was unpublished, so the
 * requirement is now a sentence to a prospective student.
 *
 * Between items of one course they are **still enforced**, and the database
 * rejects only a self-reference - so everything here involving more than one
 * edge is the application's job.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrerequisiteApiTest(
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

    /** A course with one section and one lesson item, left in draft. */
    private fun draftCourse(token: String): Pair<String, String> {
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Course ${System.nanoTime()}"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val courseId = objectMapper.readTree(body).get("id").asString()

        val sectionBody = mockMvc.post("/api/v1/courses/$courseId/sections") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"Section"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val sectionId = objectMapper.readTree(sectionBody).get("id").asString()
        return courseId to sectionId
    }

    private fun addItem(token: String, sectionId: String, title: String): String {
        val body = mockMvc.post("/api/v1/sections/$sectionId/items") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"$title","type":"LESSON"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    private fun publish(token: String, courseId: String) =
        mockMvc.post("/api/v1/courses/$courseId/publish") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }

    private fun publishedCourse(token: String): Pair<String, String> {
        val (courseId, sectionId) = draftCourse(token)
        addItem(token, sectionId, "Lesson")
        publish(token, courseId)
        return courseId to sectionId
    }

    private fun setItemPrereqs(token: String, itemId: String, ids: List<String>) =
        mockMvc.put("/api/v1/items/$itemId/prerequisites") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(mapOf("prerequisiteIds" to ids))
        }

    // ---- course prerequisites (free text, not enforced) -----------------

    private fun setCourseNotes(token: String, courseId: String, texts: List<String>) =
        mockMvc.put("/api/v1/courses/$courseId/prerequisites") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = objectMapper.writeValueAsString(mapOf("prerequisites" to texts))
        }

    @Test
    fun `course prerequisites are free text, read back publicly in order`() {
        val owner = instructorTokenFor("owner")
        val (courseId, _) = publishedCourse(owner)

        setCourseNotes(owner, courseId, listOf("Basic Python", "Comfortable with matrices"))
            .andExpect { status { isOk() } }

        // Public: "what do I need before this?" is part of deciding to sign up.
        mockMvc.get("/api/v1/courses/$courseId/prerequisites").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            // The author's ordering is meaningful - it reads as a sequence.
            jsonPath("$[0].text") { value("Basic Python") }
            jsonPath("$[1].text") { value("Comfortable with matrices") }
        }
    }

    @Test
    fun `a prerequisite never blocks enrolment`() {
        val owner = instructorTokenFor("owner")
        val student = tokenFor("student")
        val (courseId, _) = publishedCourse(owner)
        setCourseNotes(owner, courseId, listOf("A degree in astrophysics"))
            .andExpect { status { isOk() } }

        // This is the whole point of the change: a stated expectation informs a
        // prospective student, it does not gate them. Nothing to enforce means
        // nothing that can silently close a course when a dependency moves.
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `setting replaces the whole list, so the last one can be removed`() {
        val owner = instructorTokenFor("owner")
        val (courseId, _) = publishedCourse(owner)

        setCourseNotes(owner, courseId, listOf("One", "Two")).andExpect { status { isOk() } }
        setCourseNotes(owner, courseId, listOf("Only this")).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
        setCourseNotes(owner, courseId, emptyList()).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mockMvc.get("/api/v1/courses/$courseId/prerequisites").andExpect {
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `blank entries and duplicates are dropped rather than rendered`() {
        val owner = instructorTokenFor("owner")
        val (courseId, _) = publishedCourse(owner)

        // A blank would render as an empty bullet and a repeat as the same line
        // twice; both are typos rather than intent.
        setCourseNotes(owner, courseId, listOf("Basic Python", "   ", "basic python"))
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].text") { value("Basic Python") }
            }
    }

    @Test
    fun `a stranger cannot set prerequisites on someone else's course`() {
        val owner = instructorTokenFor("owner")
        val stranger = tokenFor("stranger")
        val (courseId, _) = publishedCourse(owner)
        setCourseNotes(stranger, courseId, listOf("Anything")).andExpect { status { isForbidden() } }
    }

    @Test
    fun `a draft course's prerequisites are not readable by a stranger`() {
        val owner = instructorTokenFor("owner")
        val stranger = tokenFor("stranger")
        val (draft, _) = draftCourse(owner)
        // 404, not an empty list: answering at all would confirm the draft exists.
        mockMvc.get("/api/v1/courses/$draft/prerequisites") {
            header("Authorization", "Bearer $stranger")
        }.andExpect { status { isNotFound() } }
    }

    // ---- item prerequisites ---------------------------------------------

    @Test
    fun `an item's prerequisites must belong to the same course`() {
        val owner = instructorTokenFor("owner")
        val (_, sectionA) = draftCourse(owner)
        val (_, sectionB) = draftCourse(owner)
        val itemA = addItem(owner, sectionA, "First")
        val itemB = addItem(owner, sectionB, "Elsewhere")

        setItemPrereqs(owner, itemA, listOf(itemB)).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("PREREQUISITE_OUTSIDE_COURSE") }
        }
    }

    @Test
    fun `an item loop is refused`() {
        val owner = instructorTokenFor("owner")
        val (_, sectionId) = draftCourse(owner)
        val first = addItem(owner, sectionId, "First")
        val second = addItem(owner, sectionId, "Second")

        setItemPrereqs(owner, second, listOf(first)).andExpect { status { isOk() } }
        setItemPrereqs(owner, first, listOf(second)).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("PREREQUISITE_CYCLE") }
        }
    }

    @Test
    fun `progress on a locked item is refused until its prerequisite is done`() {
        val owner = instructorTokenFor("owner")
        val student = tokenFor("student")
        val (courseId, sectionId) = draftCourse(owner)
        val first = addItem(owner, sectionId, "First")
        val second = addItem(owner, sectionId, "Second")
        setItemPrereqs(owner, second, listOf(first)).andExpect { status { isOk() } }
        publish(owner, courseId)

        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/items/$second/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("ITEM_LOCKED") }
        }

        mockMvc.post("/api/v1/items/$first/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/items/$second/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `clearing prerequisites unlocks the item again`() {
        val owner = instructorTokenFor("owner")
        val student = tokenFor("student")
        val (courseId, sectionId) = draftCourse(owner)
        val first = addItem(owner, sectionId, "First")
        val second = addItem(owner, sectionId, "Second")
        setItemPrereqs(owner, second, listOf(first)).andExpect { status { isOk() } }
        publish(owner, courseId)
        mockMvc.post("/api/v1/courses/$courseId/enroll") {
            header("Authorization", "Bearer $student")
        }.andExpect { status { isCreated() } }

        setItemPrereqs(owner, second, emptyList()).andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
        mockMvc.post("/api/v1/items/$second/progress") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $student")
            content = """{"status":"COMPLETED"}"""
        }.andExpect { status { isOk() } }
    }
}
