-- Course prerequisites become free text.
--
-- V1 modelled them as course -> course foreign keys, and enrolment enforced
-- them: you could not enter B until you had COMPLETED A. That turned out to be
-- the wrong shape for two reasons.
--
-- First, an enforced edge between two courses has no safe answer when the
-- prerequisite is later unpublished or archived. The gated course silently
-- stops accepting new students, its owner gets no signal, and the two obvious
-- repairs both hurt someone: refusing the unpublish holds one author hostage to
-- another's dependency, while quietly dropping the requirement rewrites a
-- course's pedagogy behind its author's back.
--
-- Second, nothing required the two courses to share an owner, so anyone could
-- make their course depend on somebody else's without asking.
--
-- Free text has neither problem. "Basic Python" is a statement to a prospective
-- student, not a lock, and a statement cannot be broken by someone else's
-- publish decision. Sequencing *within* a course is still enforced -- see
-- item_prerequisites, which is untouched: it stays inside one course under one
-- owner, where an ordering guarantee is both meaningful and safe.

DROP TABLE IF EXISTS course_prerequisites;

CREATE TABLE course_prerequisite_notes (
    id        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id uuid         NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    -- The author's ordering is meaningful: "Basic Python" before "Linear
    -- algebra" reads as a sequence, and a set would lose that.
    position  integer      NOT NULL CHECK (position >= 0),
    text      varchar(500) NOT NULL,
    CONSTRAINT uq_course_prerequisite_notes_position UNIQUE (course_id, position)
);

CREATE INDEX idx_course_prerequisite_notes_course ON course_prerequisite_notes (course_id);
