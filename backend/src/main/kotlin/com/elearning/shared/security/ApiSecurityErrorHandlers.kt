package com.elearning.shared.security

import com.elearning.shared.errors.ApiError
import com.elearning.shared.web.RequestIdFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Spring Security rejects requests inside the filter chain, before
 * `@RestControllerAdvice` ever runs. Without these two handlers a 401/403 comes
 * back with an empty body, so clients would face a second, undocumented error
 * shape. These make the security layer speak the same [ApiError] contract.
 */
private fun HttpServletResponse.writeApiError(
    objectMapper: ObjectMapper,
    status: HttpStatus,
    code: String,
    message: String,
) {
    this.status = status.value()
    contentType = MediaType.APPLICATION_JSON_VALUE
    characterEncoding = Charsets.UTF_8.name()
    objectMapper.writeValue(writer, ApiError(code, message, MDC.get(RequestIdFilter.MDC_KEY)))
}

/** No usable credentials were presented. */
@Component
class ApiAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) = response.writeApiError(
        objectMapper,
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED",
        "Authentication is required",
    )
}

/** Credentials were valid, but they do not grant access to this resource. */
@Component
class ApiAccessDeniedHandler(
    private val objectMapper: ObjectMapper,
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) = response.writeApiError(
        objectMapper,
        HttpStatus.FORBIDDEN,
        "FORBIDDEN",
        "You are not allowed to perform this operation",
    )
}
