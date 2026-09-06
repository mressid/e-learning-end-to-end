package com.elearning.shared.errors

import io.swagger.v3.oas.annotations.media.Schema

/**
 * The single error shape returned by every endpoint.
 *
 * Deliberately small: a stable machine-readable [code], a message safe to show
 * a user, and the [requestId] to correlate with server logs. Internal details
 * (stack traces, SQL, exception types) never appear here.
 */
@Schema(name = "ApiError", description = "Error response returned by all endpoints")
data class ApiError(
    @get:Schema(description = "Stable machine-readable error code", example = "COURSE_NOT_FOUND")
    val code: String,
    @get:Schema(description = "Human-readable message, safe to display", example = "Course not found")
    val message: String,
    @get:Schema(description = "Correlates this response with server logs")
    val requestId: String?,
    @get:Schema(description = "Field-level validation failures, when applicable")
    val errors: List<FieldError>? = null,
) {
    @Schema(name = "ApiFieldError")
    data class FieldError(
        val field: String,
        val message: String,
    )
}
