-- Students and instructors as separate account kinds, not one row with a flag.
--
-- V11 made "instructor" a boolean on `users`, which meant an administrator
-- could flip a learner into an author and back. That is the thing being
-- removed: the two are different kinds of account, decided when the account is
-- created and never afterwards. Someone who learns *and* teaches holds two
-- accounts, exactly as they hold two roles.
--
-- The shape is JPA's JOINED inheritance, and the same one the spec already uses
-- for `user_profiles`: `users` keeps authentication - it is still what a login
-- looks up and what every existing foreign key points at - and a satellite
-- table per kind carries what only that kind has. `user_type` is the
-- discriminator, so the kind is readable without a join.

-- ---------------------------------------------------------------------------
-- 1. The discriminator
-- ---------------------------------------------------------------------------

ALTER TABLE users
    ADD COLUMN user_type varchar(32) NOT NULL DEFAULT 'STUDENT'
        CHECK (user_type IN ('STUDENT', 'INSTRUCTOR'));

COMMENT ON COLUMN users.user_type IS
    'Which kind of account this is. Fixed at creation: there is no supported '
    'transition between STUDENT and INSTRUCTOR. Someone who does both holds '
    'two accounts.';

-- Everyone V11 flagged is an instructor, and so is every co-instructor. V11
-- backfilled its flag from `courses.owner_id` alone, so a co-instructor who
-- never owned a course was missed - they are picked up here, before the
-- foreign keys below start insisting on it.
UPDATE users
SET user_type = 'INSTRUCTOR'
WHERE is_instructor
   OR id IN (SELECT owner_id FROM courses)
   OR id IN (SELECT instructor_id FROM course_instructors);

-- ---------------------------------------------------------------------------
-- 2. The satellite tables
-- ---------------------------------------------------------------------------

-- Both are thin today. They exist so the two kinds have somewhere of their own
-- to grow - an instructor headline or payout details, a student's learning
-- preferences - without widening the table every authenticated request reads.

CREATE TABLE students (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE instructors (
    user_id uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE
);

INSERT INTO students (user_id)
SELECT id FROM users WHERE user_type = 'STUDENT';

INSERT INTO instructors (user_id)
SELECT id FROM users WHERE user_type = 'INSTRUCTOR';

-- ---------------------------------------------------------------------------
-- 3. A student cannot author a course
-- ---------------------------------------------------------------------------

-- Previously "may author courses" was a boolean the application checked, and
-- creating a course silently set it. Now it is the database's answer: these
-- columns reference `instructors`, so a student id in either one is rejected
-- by the constraint rather than by whichever code path remembered to look.
--
-- Both already pointed at `users (id)`; re-pointing them at `instructors
-- (user_id)` keeps the same target row, since every instructor is still a user.

ALTER TABLE courses
    DROP CONSTRAINT courses_owner_id_fkey,
    ADD CONSTRAINT courses_owner_id_fkey
        FOREIGN KEY (owner_id) REFERENCES instructors (user_id) ON DELETE RESTRICT;

ALTER TABLE course_instructors
    DROP CONSTRAINT course_instructors_instructor_id_fkey,
    ADD CONSTRAINT course_instructors_instructor_id_fkey
        FOREIGN KEY (instructor_id) REFERENCES instructors (user_id) ON DELETE CASCADE;

-- ---------------------------------------------------------------------------
-- 4. The flag is gone
-- ---------------------------------------------------------------------------

DROP INDEX idx_users_is_instructor;

ALTER TABLE users
    DROP COLUMN is_instructor;

-- The learner directory is now "every student", which is most of the table, so
-- it pages rather than filters and needs no index of its own. This one is for
-- the opposite question - the instructor roster - which `instructors` answers
-- directly. What is left is the discriminator, read on lookups that have a user
-- in hand and want to know which kind it is.
CREATE INDEX idx_users_user_type ON users (user_type);
