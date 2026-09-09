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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import tools.jackson.databind.ObjectMapper

/**
 * Co-instructors, and the profile endpoints.
 *
 * `CourseAuthorization` has always trusted `course_instructors`, but until now
 * nothing could write a row there, so co-teaching was unreachable.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseInstructorApiTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val objectMapper: ObjectMapper,
    @Autowired val accounts: TestAccounts,
) : IntegrationTest() {

    private val password = "correct horse battery staple"

    private data class Account(val token: String, val userId: String)

    private fun account(label: String): Account {
        val unique = "$label-${System.nanoTime()}"
        val created = mockMvc.post("/api/v1/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","username":"$unique","password":"$password"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andReturn().response.contentAsString
        return Account(
            objectMapper.readTree(body).get("accessToken").asString(),
            objectMapper.readTree(created).get("id").asString(),
        )
    }

    /**
     * An instructor account.
     *
     * Owners and co-instructors both have to be one: `courses.owner_id` and
     * `course_instructors.instructor_id` reference the instructor table, so a
     * student in either position is refused by the database as well as the
     * service. [account] stays the student, for the callers that want one.
     */
    private fun instructorAccount(label: String): Account {
        val unique = "$label-${System.nanoTime()}"
        val userId = accounts.instructor("$unique@example.com", unique, password)
        val body = mockMvc.post("/api/v1/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$unique@example.com","password":"$password"}"""
        }.andReturn().response.contentAsString
        return Account(objectMapper.readTree(body).get("accessToken").asString(), userId)
    }

    private fun createCourse(token: String): String {
        val body = mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $token")
            content = """{"title":"C ${System.nanoTime()}"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return objectMapper.readTree(body).get("id").asString()
    }

    private fun addInstructor(courseId: String, actor: String, instructorId: String, role: String = "ASSISTANT") =
        mockMvc.post("/api/v1/courses/$courseId/instructors") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer $actor")
            content = """{"instructorId":"$instructorId","role":"$role"}"""
        }

    @Test
    fun `a student cannot be added as a co-instructor`() {
        val owner = instructorAccount("owner")
        val student = account("student")
        val courseId = createCourse(owner.token)

        // Rejected for what the account is, not for anything about this course:
        // the owner is asking, so the ownership check has already passed.
        addInstructor(courseId, owner.token, student.userId).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_AN_INSTRUCTOR") }
        }

        mockMvc.get("/api/v1/courses/$courseId/instructors") {
            header("Authorization", "Bearer ${owner.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `a student cannot create a course at all`() {
        val student = account("student")
        mockMvc.post("/api/v1/courses") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${student.token}")
            content = """{"title":"Not mine to write"}"""
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("NOT_AN_INSTRUCTOR") }
        }
    }

    @Test
    fun `a co-instructor can edit the course, closing the gap authorization already assumed`() {
        val owner = instructorAccount("owner")
        val assistant = instructorAccount("assistant")
        val courseId = createCourse(owner.token)

        // Before being added, they are a stranger.
        mockMvc.patch("/api/v1/courses/$courseId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${assistant.token}")
            content = """{"title":"Nope"}"""
        }.andExpect { status { isForbidden() } }

        addInstructor(courseId, owner.token, assistant.userId).andExpect {
            status { isCreated() }
            jsonPath("$.role") { value("ASSISTANT") }
        }

        mockMvc.patch("/api/v1/courses/$courseId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${assistant.token}")
            content = """{"title":"Edited by the assistant"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.title") { value("Edited by the assistant") }
        }
    }

    @Test
    fun `only the owner may manage the roster`() {
        val owner = instructorAccount("owner")
        val assistant = instructorAccount("assistant")
        val outsider = account("outsider")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, assistant.userId).andExpect { status { isCreated() } }

        // A co-instructor handing out authority over someone else's course would
        // let one assistant quietly expand the roster.
        addInstructor(courseId, assistant.token, outsider.userId).andExpect {
            status { isForbidden() }
            jsonPath("$.code") { value("COURSE_OWNER_ONLY") }
        }

        // They can still see who is on it.
        mockMvc.get("/api/v1/courses/$courseId/instructors") {
            header("Authorization", "Bearer ${assistant.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
        }
    }

    @Test
    fun `the owner is not added as an instructor`() {
        val owner = instructorAccount("owner")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, owner.userId).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("OWNER_IS_NOT_AN_INSTRUCTOR") }
        }
    }

    @Test
    fun `an unknown user cannot be added`() {
        val owner = instructorAccount("owner")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, "00000000-0000-0000-0000-000000000000").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `adding twice updates the role rather than duplicating`() {
        val owner = instructorAccount("owner")
        val assistant = instructorAccount("assistant")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, assistant.userId, "ASSISTANT").andExpect { status { isCreated() } }
        addInstructor(courseId, owner.token, assistant.userId, "PRIMARY").andExpect {
            status { isCreated() }
            jsonPath("$.role") { value("PRIMARY") }
        }

        mockMvc.get("/api/v1/courses/$courseId/instructors") {
            header("Authorization", "Bearer ${owner.token}")
        }.andExpect { jsonPath("$.length()") { value(1) } }
    }

    @Test
    fun `removing revokes edit access`() {
        val owner = instructorAccount("owner")
        val assistant = instructorAccount("assistant")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, assistant.userId).andExpect { status { isCreated() } }

        mockMvc.delete("/api/v1/courses/$courseId/instructors/${assistant.userId}") {
            header("Authorization", "Bearer ${owner.token}")
        }.andExpect { status { isNoContent() } }

        mockMvc.patch("/api/v1/courses/$courseId") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${assistant.token}")
            content = """{"title":"Still?"}"""
        }.andExpect { status { isForbidden() } }
    }

    // ---- profile ---------------------------------------------------------

    @Test
    fun `a profile can be read and updated`() {
        val user = account("user")

        mockMvc.get("/api/v1/me/profile") {
            header("Authorization", "Bearer ${user.token}")
        }.andExpect {
            status { isOk() }
            jsonPath("$.timezone") { value("UTC") }
        }

        mockMvc.patch("/api/v1/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${user.token}")
            content = """{"firstName":"Ada","lastName":"Lovelace","timezone":"Europe/London"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.displayName") { value("Ada Lovelace") }
            jsonPath("$.timezone") { value("Europe/London") }
        }

        // A partial update leaves the rest alone.
        mockMvc.patch("/api/v1/me/profile") {
            contentType = MediaType.APPLICATION_JSON
            header("Authorization", "Bearer ${user.token}")
            content = """{"bio":"Mathematician"}"""
        }.andExpect {
            jsonPath("$.bio") { value("Mathematician") }
            jsonPath("$.firstName") { value("Ada") }
        }
    }

    @Test
    fun `profile endpoints require authentication`() {
        mockMvc.get("/api/v1/me/profile").andExpect { status { isUnauthorized() } }
    }
}
