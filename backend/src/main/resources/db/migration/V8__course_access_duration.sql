-- How long a course grants access for.
--
-- `enrollments.expires_at` has been in the schema since V1 and nothing ever set
-- it, so the column was always null, `Enrollment.isClosed()` never fired on it
-- and `EnrollmentExpirySweeper` swept nothing. The mechanism was complete and
-- the policy was missing; this supplies the policy.
--
-- NULL means access does not lapse, which is both the sensible default and what
-- every existing enrolment already had. Setting a duration is opting in.

ALTER TABLE courses
    ADD COLUMN access_duration_days integer
        CHECK (access_duration_days IS NULL OR access_duration_days > 0);

COMMENT ON COLUMN courses.access_duration_days IS
    'Days of access granted at enrolment. NULL means access never lapses. '
    'Copied onto enrollments.expires_at when a student enrols, not read live, '
    'so changing it never shortens access somebody already holds.';
