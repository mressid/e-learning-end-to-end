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
    fun `a co-instructor can edit the course, closing the gap authorization already assumed`() {
        val owner = account("owner")
        val assistant = account("assistant")
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
        val owner = account("owner")
        val assistant = account("assistant")
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
        val owner = account("owner")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, owner.userId).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("OWNER_IS_NOT_AN_INSTRUCTOR") }
        }
    }

    @Test
    fun `an unknown user cannot be added`() {
        val owner = account("owner")
        val courseId = createCourse(owner.token)
        addInstructor(courseId, owner.token, "00000000-0000-0000-0000-000000000000").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("USER_NOT_FOUND") }
        }
    }

    @Test
    fun `adding twice updates the role rather than duplicating`() {
        val owner = account("owner")
        val assistant = account("assistant")
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
        val owner = account("owner")
        val assistant = account("assistant")
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
