package com.elearning.identity.domain

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table

/**
 * Someone who takes courses.
 *
 * The table is empty beyond the key it shares with [User], and deliberately so:
 * it exists to make "is this account a student" a row that either is or is not
 * there, and to give student-only data - learning preferences, say - somewhere
 * to go that is not the table read on every authenticated request.
 */
@Entity
@Table(name = "students")
@DiscriminatorValue("STUDENT")
// The key column is `user_id`, not the `id` a joined subclass would default to:
// it is the same row as the `users` one and reads better said that way.
@PrimaryKeyJoinColumn(name = "user_id")
class Student(
    email: String,
    username: String,
    passwordHash: String,
    status: UserStatus = UserStatus.PENDING,
) : User(email, username, passwordHash, status) {

    override val type: UserType get() = UserType.STUDENT
}
