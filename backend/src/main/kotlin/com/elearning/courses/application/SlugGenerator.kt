package com.elearning.courses.application

import com.elearning.courses.infrastructure.CourseRepository
import org.springframework.stereotype.Component
import java.text.Normalizer
import java.util.Locale

/**
 * Turns a title into a URL-safe, unique slug.
 *
 * Slugs are part of public URLs, so they are generated once and never change
 * with the title - renaming a course must not break existing links.
 */
@Component
class SlugGenerator(private val courses: CourseRepository) {

    fun uniqueSlugFor(title: String): String {
        val base = slugify(title).ifBlank { "course" }
        if (!courses.existsBySlug(base)) return base

        // Collisions are rare; a short numeric suffix keeps the slug readable.
        var suffix = 2
        while (courses.existsBySlug("$base-$suffix")) {
            suffix++
        }
        return "$base-$suffix"
    }

    companion object {
        /**
         * Shared with the taxonomy: `categories.slug` and `tags.slug` are
         * narrower columns than `courses.slug`, so the cap is a parameter -
         * a 200-character tag slug would be rejected by the database.
         */
        fun slugify(value: String, maxLength: Int = MAX_LENGTH): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                // Strip accents so "Programmation Avancée" becomes "programmation-avancee".
                .replace(DIACRITICS, "")
                .lowercase(Locale.ROOT)
                .replace(NON_ALPHANUMERIC, "-")
                .trim('-')
                .take(maxLength)
                .trim('-')

        private val DIACRITICS = "\\p{InCombiningDiacriticalMarks}+".toRegex()
        private val NON_ALPHANUMERIC = "[^a-z0-9]+".toRegex()
        private const val MAX_LENGTH = 200
    }
}
