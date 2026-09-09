package com.elearning.identity.domain

/**
 * The two kinds of platform account.
 *
 * Not a role that can be granted or revoked: it is what the account *is*, fixed
 * when it is created. Someone who teaches and also wants to take courses holds
 * two accounts, because the alternative - one account that is sometimes an
 * author - is what made "is this person allowed to author" a question with a
 * changing answer.
 *
 * Dashboard administrators are not here at all. They are a separate table
 * (`admin_users`) with its own roles and permissions; this is the public side
 * of the platform.
 */
enum class UserType {
    STUDENT,
    INSTRUCTOR,
}
