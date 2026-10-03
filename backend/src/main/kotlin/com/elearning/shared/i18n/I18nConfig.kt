package com.elearning.shared.i18n

import org.springframework.context.MessageSource
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.ReloadableResourceBundleMessageSource
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Internationalization and runtime message resolution.
 *
 * Resolves locale from the `Accept-Language` HTTP header, defaulting to English.
 * Supported locales: English (en) and French (fr).
 */
@Configuration
class I18nConfig {

    @Bean
    fun localeResolver(): LocaleResolver =
        AcceptHeaderLocaleResolver().apply {
            setDefaultLocale(Locale.ENGLISH)
            supportedLocales = listOf(Locale.ENGLISH, Locale.FRENCH)
        }

    @Bean
    fun messageSource(): MessageSource =
        ReloadableResourceBundleMessageSource().apply {
            setBasename("classpath:i18n/messages")
            setDefaultEncoding(StandardCharsets.UTF_8.name())
            setFallbackToSystemLocale(false)
            setDefaultLocale(Locale.ENGLISH)
        }
}
