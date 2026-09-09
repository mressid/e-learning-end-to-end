-- ============================================================================
-- Completion rules that the platform actually applies
-- ============================================================================
-- The rule was stored on every lesson, returned by the API, and read by
-- nothing: progress was whatever a client said it was. A setting that makes a
-- promise and changes no behaviour is worse than no setting, because the
-- author believes the promise.
--
-- Three of the four are enforceable from what a lesson already knows, and are
-- enforced now. PERCENTAGE is not: it says a student must get a set share of
-- the way through and there is nowhere to record what share. Rather than
-- invent a threshold nobody chose, it goes; the lessons using it fall back to
-- the student marking their own progress, which is what was happening anyway.
-- ============================================================================

UPDATE lessons SET completion_rule = 'MANUAL' WHERE completion_rule = 'PERCENTAGE';

ALTER TABLE lessons DROP CONSTRAINT lessons_completion_rule_check;
ALTER TABLE lessons ADD CONSTRAINT lessons_completion_rule_check
    CHECK (completion_rule IN ('MANUAL', 'VIEW', 'DURATION'));
