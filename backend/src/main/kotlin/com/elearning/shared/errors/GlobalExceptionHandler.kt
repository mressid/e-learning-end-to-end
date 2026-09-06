package com.elearning.shared.errors

import com.elearning.shared.web.RequestIdFilter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * Turns exceptions into the one [ApiError] shape (AGENT.md §15).
 *
 * Rule: anything not explicitly handled becomes a generic 500 whose body says
 * nothing about the cause. The detail goes to the log, keyed by request id.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException): ResponseEntity<ApiError> {
        // Expected, deliberate outcomes: log at debug, not as errors.
        log.debug("{} -> {}", ex.code, ex.message)
        return respond(ex.status, ex.code, ex.message)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fieldErrors = ex.bindingResult.fieldErrors.map {
            ApiError.FieldError(it.field, it.defaultMessage ?: "is invalid")
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiError(
                code = "VALIDATION_FAILED",
                message = "Request validation failed",
                requestId = requestId(),
                errors = fieldErrors,
            ),
        )
    }

    /**
     * Constraint violations on `@RequestParam`/`@PathVariable`. Spring validates
     * these itself and the exception already means "bad request" - without this
     * it would fall through to the catch-all and be reported as a server error.
     */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(ex: HandlerMethodValidationException): ResponseEntity<ApiError> {
        val fieldErrors = ex.parameterValidationResults.flatMap { result ->
            val name = result.methodParameter.parameterName ?: "parameter"
            result.resolvableErrors.map { ApiError.FieldError(name, it.defaultMessage ?: "is invalid") }
        }
        return ResponseEntity.badRequest().body(
            ApiError("VALIDATION_FAILED", "Request validation failed", requestId(), fieldErrors),
        )
    }

    /** Unparseable body, or a value that does not fit the target type. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiError> {
        // The parser message can quote the payload, so it is logged, not returned.
        log.debug("Unreadable request body", ex)
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body could not be parsed")
    }

    /** A path variable or query parameter of the wrong type, e.g. a malformed UUID. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest().body(
            ApiError(
                "INVALID_PARAMETER",
                "Request parameter is invalid",
                requestId(),
                listOf(ApiError.FieldError(ex.name, "has an invalid value")),
            ),
        )

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupported(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ApiError> =
        respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "That method is not supported on this endpoint")

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthentication(ex: AuthenticationException): ResponseEntity<ApiError> =
        respond(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required")

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiError> =
        respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "You are not allowed to perform this operation")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResource(ex: NoResourceFoundException): ResponseEntity<ApiError> =
        respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found")

    /**
     * A unique or foreign-key violation that slipped past an application check -
     * a race on a uniqueness test, or a reference that vanished. It is a conflict
     * about the request, not a server fault, and the driver's message can quote
     * table and column names, so only a generic message is returned.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiError> {
        log.warn("Constraint violation reached the database", ex)
        return respond(HttpStatus.CONFLICT, "CONSTRAINT_VIOLATION", "The request conflicts with existing data")
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ApiError> {
        // The only place the real cause is recorded. Never sent to the client.
        log.error("Unhandled exception", ex)
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred")
    }

    private fun respond(status: HttpStatus, code: String, message: String) =
        ResponseEntity.status(status).body(ApiError(code, message, requestId()))

    private fun requestId(): String? = MDC.get(RequestIdFilter.MDC_KEY)
}
