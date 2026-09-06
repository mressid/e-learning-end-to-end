-- The audit trail behind the dashboard's /audit-logs page.
--
-- Append-only by construction: there is no endpoint that updates or deletes a
-- row here, because a log somebody can edit is not evidence of anything.
--
-- Note what this table deliberately does NOT have: a foreign key on actor_id.
-- Two reasons, and the second is the important one.
--
--   1. An actor may be an administrator or a learner, and those are separate
--      tables. A column that might reference either cannot carry a key.
--   2. **The record has to outlive its subject.** If an administrator's account
--      is deleted, what they did must remain on file - an FK would either
--      cascade the history away or block the deletion. Neither is what an audit
--      trail is for.
--
-- The same reasoning drives actor_label: it snapshots who the actor *was* at
-- the time. Resolving the name live would let a later rename rewrite the past,
-- and would leave deleted accounts as anonymous UUIDs.

CREATE TABLE audit_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at  timestamptz  NOT NULL DEFAULT now(),

    actor_type   varchar(16)  NOT NULL
        CHECK (actor_type IN ('ADMIN', 'USER', 'SYSTEM')),
    actor_id     uuid,
    -- Who they were when it happened, not who that id resolves to today.
    actor_label  varchar(320),

    -- Dotted, matching the permission that gates the action where there is one
    -- ('role.assigned', 'user.suspended'), so a reader can connect the two.
    action       varchar(64)  NOT NULL,
    target_type  varchar(32),
    target_id    uuid,
    summary      varchar(500) NOT NULL,

    -- Whatever the action needs to be understood later, without a column per
    -- action type - the same reasoning notifications.data follows.
    details      jsonb        NOT NULL DEFAULT '{}'::jsonb,

    -- Ties an entry to the request that produced it, and to the application
    -- logs, which already carry the same id in their MDC.
    request_id   varchar(64)
);

-- The page reads newest-first and filters; these are the two axes it offers.
CREATE INDEX idx_audit_log_occurred ON audit_log (occurred_at DESC);
CREATE INDEX idx_audit_log_action   ON audit_log (action, occurred_at DESC);
CREATE INDEX idx_audit_log_actor    ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_log_target   ON audit_log (target_type, target_id);
