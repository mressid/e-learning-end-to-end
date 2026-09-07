-- Video transcoding work.
--
-- A separate table rather than another `media_objects.status` value, because
-- these describe different things: a media status says whether an upload
-- arrived, while a job has attempts, an error to report, and a lifetime longer
-- than the request that created it.
--
-- The row is written before the queue message, always. The notification
-- pipeline learned this the hard way - the worker is fast, regularly won the
-- race against its own transaction, found no row and discarded the message.

CREATE TABLE transcode_jobs (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- The source upload. CASCADE because a job for a deleted file is noise.
    media_id       uuid NOT NULL REFERENCES media_objects (id) ON DELETE CASCADE,
    -- Where the results belong. Nullable so the pipeline can later serve
    -- something other than a lesson without a schema change.
    lesson_id      uuid REFERENCES lessons (course_item_id) ON DELETE CASCADE,

    status         varchar(16) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    attempts       integer     NOT NULL DEFAULT 0,
    -- Kept so a failure can be explained without reading worker logs.
    error          varchar(1000),

    created_at     timestamptz NOT NULL DEFAULT now(),
    started_at     timestamptz,
    finished_at    timestamptz,

    -- One live job per source: re-attaching the same video to the same lesson
    -- should not queue the work twice.
    CONSTRAINT uq_transcode_jobs_media_lesson UNIQUE (media_id, lesson_id)
);

CREATE INDEX idx_transcode_jobs_status ON transcode_jobs (status, created_at);
