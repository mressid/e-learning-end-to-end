package com.elearning.shared.errors

import org.springframework.http.HttpStatus

/**
 * Base class for failures that map to a deliberate API response.
 *
 * Business code throws these; [GlobalExceptionHandler] turns them into an
 * [ApiError]. Anything else that escapes becomes a generic 500.
 */
open class ApiException(
    val code: String,
    val status: HttpStatus,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** The requested resource does not exist, or the caller may not know that it does. */
class NotFoundException(code: String, message: String) :
    ApiException(code, HttpStatus.NOT_FOUND, message)

/** The request conflicts with current state, e.g. a duplicate enrollment. */
class ConflictException(code: String, message: String) :
    ApiException(code, HttpStatus.CONFLICT, message)

/** The caller is authenticated but not allowed to act on this specific resource. */
class ForbiddenException(code: String, message: String) :
    ApiException(code, HttpStatus.FORBIDDEN, message)

/** The request is well-formed but violates a business rule. */
class BusinessRuleException(code: String, message: String) :
    ApiException(code, HttpStatus.UNPROCESSABLE_ENTITY, message)
