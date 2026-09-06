package com.elearning.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Gives every request an id, exposed three ways: in the log MDC, on the
 * response as `X-Request-Id`, and in the body of any [com.elearning.shared.errors.ApiError].
 * That is what makes a user-reported error traceable to a log line.
 *
 * An inbound `X-Request-Id` is honoured so a request can be followed across
 * services, but it is length-capped and sanitised: it reaches the logs, and
 * untrusted input must not be able to forge log content.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = sanitise(request.getHeader(HEADER)) ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, requestId)
        response.setHeader(HEADER, requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun sanitise(value: String?): String? = value
        ?.trim()
        ?.take(MAX_LENGTH)
        ?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
        ?.ifEmpty { null }

    companion object {
        const val HEADER = "X-Request-Id"
        const val MDC_KEY = "requestId"
        private const val MAX_LENGTH = 64
    }
}
