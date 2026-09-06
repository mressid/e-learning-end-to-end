package com.elearning.identity.domain

/** Mirrors the `users.status` check constraint. */
enum class UserStatus {
    ACTIVE,
    SUSPENDED,
    DISABLED,
    PENDING,
}
