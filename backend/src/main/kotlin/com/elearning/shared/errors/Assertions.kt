package com.elearning.shared.errors

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Asserts that a business rule condition is met.
 * Throws [BusinessRuleException] (HTTP 422) if false.
 */
@OptIn(ExperimentalContracts::class)
inline fun requireRule(value: Boolean, code: String, vararg args: Any, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw BusinessRuleException(code, lazyMessage(), *args)
    }
}

@OptIn(ExperimentalContracts::class)
fun requireRule(value: Boolean, code: String, message: String = "", vararg args: Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw BusinessRuleException(code, message, *args)
    }
}

/**
 * Asserts that a business rule condition on a nullable value is met.
 * Throws [BusinessRuleException] (HTTP 422) if null, otherwise returns the non-null value.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireRule(value: T?, code: String, vararg args: Any, lazyMessage: () -> String): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw BusinessRuleException(code, lazyMessage(), *args)
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> requireRule(value: T?, code: String, message: String = "", vararg args: Any): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw BusinessRuleException(code, message, *args)
}

/**
 * Asserts that a resource is found.
 * Throws [NotFoundException] (HTTP 404) if false.
 */
@OptIn(ExperimentalContracts::class)
inline fun requireFound(value: Boolean, code: String, vararg args: Any, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw NotFoundException(code, lazyMessage(), *args)
    }
}

@OptIn(ExperimentalContracts::class)
fun requireFound(value: Boolean, code: String, message: String = "Resource not found", vararg args: Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw NotFoundException(code, message, *args)
    }
}

/**
 * Asserts that an entity or required value is present.
 * Throws [NotFoundException] (HTTP 404) if null, otherwise returns the non-null value.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireFound(
    value: T?,
    code: String,
    vararg args: Any,
    lazyMessage: () -> String = { "Resource not found" },
): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw NotFoundException(code, lazyMessage(), *args)
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> requireFound(value: T?, code: String, message: String = "Resource not found", vararg args: Any): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw NotFoundException(code, message, *args)
}

/**
 * Asserts that no state conflict exists.
 * Throws [ConflictException] (HTTP 409) if false.
 */
@OptIn(ExperimentalContracts::class)
inline fun requireNoConflict(value: Boolean, code: String, vararg args: Any, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw ConflictException(code, lazyMessage(), *args)
    }
}

@OptIn(ExperimentalContracts::class)
fun requireNoConflict(value: Boolean, code: String, message: String = "", vararg args: Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw ConflictException(code, message, *args)
    }
}

/**
 * Asserts that an authorization or access condition is satisfied.
 * Throws [ForbiddenException] (HTTP 403) if false.
 */
@OptIn(ExperimentalContracts::class)
inline fun requireAllowed(value: Boolean, code: String, vararg args: Any, lazyMessage: () -> String) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw ForbiddenException(code, lazyMessage(), *args)
    }
}

@OptIn(ExperimentalContracts::class)
fun requireAllowed(value: Boolean, code: String, message: String = "", vararg args: Any) {
    contract {
        returns() implies value
    }
    if (!value) {
        throw ForbiddenException(code, message, *args)
    }
}

/**
 * Asserts that an authorized entity or value is present.
 * Throws [ForbiddenException] (HTTP 403) if null, otherwise returns the non-null value.
 */
@OptIn(ExperimentalContracts::class)
inline fun <T : Any> requireAllowed(
    value: T?,
    code: String,
    vararg args: Any,
    lazyMessage: () -> String = { "Access denied" },
): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw ForbiddenException(code, lazyMessage(), *args)
}

@OptIn(ExperimentalContracts::class)
fun <T : Any> requireAllowed(value: T?, code: String, message: String = "Access denied", vararg args: Any): T {
    contract {
        returns() implies (value != null)
    }
    return value ?: throw ForbiddenException(code, message, *args)
}
