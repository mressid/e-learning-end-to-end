package com.elearning.shared.i18n

import com.elearning.shared.testing.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.util.Locale
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class I18nErrorHandlingTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val translationService: TranslationService,
) : IntegrationTest() {

    @Test
    fun `returns translated French error message when Accept-Language is fr`() {
        val nonExistentId = UUID.randomUUID()
        mockMvc.get("/api/v1/courses/$nonExistentId") {
            header("Accept-Language", "fr")
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("COURSE_NOT_FOUND") }
            jsonPath("$.message") { value("Cours introuvable") }
        }
    }

    @Test
    fun `returns default English error message when Accept-Language is missing`() {
        val nonExistentId = UUID.randomUUID()
        mockMvc.get("/api/v1/courses/$nonExistentId")
            .andExpect {
                status { isNotFound() }
                jsonPath("$.code") { value("COURSE_NOT_FOUND") }
                jsonPath("$.message") { value("Course not found") }
            }
    }

    @Test
    fun `translation service resolves messages and parametric placeholders`() {
        val frEnrollment = translationService.get("ALREADY_ENROLLED", locale = Locale.FRENCH)
        assertThat(frEnrollment).isEqualTo("Vous êtes déjà inscrit à ce cours")

        val enEnrollment = translationService.get("ALREADY_ENROLLED", locale = Locale.ENGLISH)
        assertThat(enEnrollment).isEqualTo("You are already enrolled in this course")

        val frParametric = translationService.get("CATEGORY_IN_USE", 5, locale = Locale.FRENCH)
        assertThat(frParametric).isEqualTo("5 cours sont encore associés à cette catégorie")

        val enParametric = translationService.get("CATEGORY_IN_USE", 3, locale = Locale.ENGLISH)
        assertThat(enParametric).isEqualTo("3 course(s) are still in this category")
    }
}
