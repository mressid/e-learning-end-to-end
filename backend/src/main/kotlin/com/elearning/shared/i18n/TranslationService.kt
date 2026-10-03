package com.elearning.shared.i18n

import org.springframework.context.MessageSource
import org.springframework.context.NoSuchMessageException
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.stereotype.Service
import java.util.Locale

/**
 * Helper service for programmatically resolving localized messages at runtime.
 * Can be used in HTTP requests, asynchronous listeners, emails, and notifications.
 */
@Service
class TranslationService(
    private val messageSource: MessageSource,
) {

    /**
     * Resolves a message for the given [code] and [args].
     *
     * Falls back to [defaultMessage] or [code] if no message translation is found.
     */
    fun get(
        code: String,
        vararg args: Any,
        defaultMessage: String? = null,
        locale: Locale = LocaleContextHolder.getLocale(),
    ): String {
        return try {
            val messageArgs: Array<out Any>? = if (args.isEmpty()) null else args
            messageSource.getMessage(code, messageArgs, locale)
        } catch (ex: NoSuchMessageException) {
            defaultMessage ?: code
        }
    }
}
