-- Who may author courses, as a fact about the person rather than a side effect.
--
-- Until now "instructor" was derived: you were one if you owned at least one
-- course. That reads well until an administrator needs to enrol a teacher
-- *before* they have anything to teach - there was nowhere to record the
-- intent, and the roster could not show them at all.
--
-- The flag does not replace the relationship checks. Authority over a
-- particular course still comes from owning it or being a co-instructor on it
-- (AGENTS.md §11); this only says who is allowed to start one, which no
-- relationship can express because the course does not exist yet.

ALTER TABLE users
    ADD COLUMN is_instructor boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN users.is_instructor IS
    'May author courses. Not authority over any particular course - that is '
    'still ownership or co-instructorship. Set by an administrator holding '
    'user.write.';

-- Backfill, so the change is invisible to anyone already teaching. Deriving it
-- from ownership reproduces exactly the set the old roster query returned.
UPDATE users
SET is_instructor = true
WHERE id IN (SELECT DISTINCT owner_id FROM courses);

-- Partial: the roster only ever asks for the true rows, and instructors are the
-- small minority of a user table.
CREATE INDEX idx_users_is_instructor ON users (is_instructor) WHERE is_instructor;
