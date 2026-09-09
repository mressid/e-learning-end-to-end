package com.elearning.identity.domain

import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table

/**
 * Someone who authors courses.
 *
 * Being one of these is what allows authoring - and it is the only thing that
 * does. `courses.owner_id` and `course_instructors.instructor_id` both
 * reference this table, so a student cannot be given a course by any code path,
 * including one that forgets to check.
 *
 * It remains separate from authority over a *particular* course, which is still
 * ownership or co-instructorship (AGENTS.md §11). This says who may start one,
 * which no relationship can express because the course does not exist yet.
 */
@Entity
@Table(name = "instructors")
@DiscriminatorValue("INSTRUCTOR")
// The key column is `user_id`, not the `id` a joined subclass would default to:
// it is the same row as the `users` one and reads better said that way.
@PrimaryKeyJoinColumn(name = "user_id")
class Instructor(
    email: String,
    username: String,
    passwordHash: String,
    status: UserStatus = UserStatus.PENDING,
) : User(email, username, passwordHash, status) {

    override val type: UserType get() = UserType.INSTRUCTOR
}
