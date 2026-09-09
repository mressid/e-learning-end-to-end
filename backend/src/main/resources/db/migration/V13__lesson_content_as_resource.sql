-- ============================================================================
-- A lesson's body becomes a resource, and renditions move to the file
-- ============================================================================
-- The schema described the same idea twice. `resources` splits what a material
-- IS (document, video, audio, image, link) from where it LIVES (an uploaded
-- file, a URL, text held inline), and records the format of inline text.
-- `lessons.content_type` collapsed both questions into one list, which is why
-- two of its five values had no table behind them: ARTICLE is a kind of thing,
-- EXTERNAL is a place a thing lives, and they were never on the same axis.
--
-- So a lesson keeps what is genuinely its own - a description, how long it
-- takes, when it counts as done - and points at a resource for the material
-- itself. Audio and external lessons work from here on because the resource
-- model already knew how to hold them.
--
-- The three per-lesson content tables go. Two of them held a single column, and
-- the video one held renditions that were never the lesson's business: an
-- encoded manifest and a poster frame belong to the file they were made from,
-- which is why two lessons sharing a video encoded it twice.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Renditions belong to the media object
-- ---------------------------------------------------------------------------

ALTER TABLE media_objects
    ADD COLUMN hls_manifest_media_id uuid REFERENCES media_objects (id) ON DELETE SET NULL,
    ADD COLUMN poster_media_id       uuid REFERENCES media_objects (id) ON DELETE SET NULL,
    ADD COLUMN duration_seconds      integer CHECK (duration_seconds >= 0);

COMMENT ON COLUMN media_objects.duration_seconds IS
    'Runtime discovered by transcoding. The length an author states for a lesson is lessons.duration_seconds.';

-- Where two lessons pointed at one file, they had a rendition each and only one
-- survives. They were renditions of identical bytes, so either will do.
UPDATE media_objects m
SET hls_manifest_media_id = vc.hls_manifest_media_id,
    poster_media_id       = vc.thumbnail_media_id,
    duration_seconds      = vc.duration_seconds
FROM video_contents vc
WHERE vc.media_id = m.id;

CREATE INDEX idx_media_objects_hls_manifest ON media_objects (hls_manifest_media_id);
CREATE INDEX idx_media_objects_poster ON media_objects (poster_media_id);

-- ---------------------------------------------------------------------------
-- 2. Every existing body becomes a resource
-- ---------------------------------------------------------------------------

ALTER TABLE lessons
    ADD COLUMN primary_resource_id uuid REFERENCES resources (id) ON DELETE RESTRICT;

-- The lesson's own id is reused as its resource's id. It saves carrying a
-- mapping table through the statements below, and a collision with an existing
-- resource would fail this INSERT on the primary key rather than pass silently.
INSERT INTO resources (id, title, resource_type, source_type, created_by)
SELECT l.course_item_id, ci.title, 'DOCUMENT', 'INLINE', c.owner_id
FROM lessons l
         JOIN article_contents ac ON ac.lesson_id = l.course_item_id
         JOIN course_items ci ON ci.id = l.course_item_id
         JOIN course_sections cs ON cs.id = ci.section_id
         JOIN courses c ON c.id = cs.course_id
WHERE l.content_type = 'ARTICLE';

-- Markdown by convention until now: the editor wrote it and nothing recorded
-- it. Written down at last.
INSERT INTO resource_contents (resource_id, content_type, content)
SELECT l.course_item_id, 'MARKDOWN', ac.content
FROM lessons l
         JOIN article_contents ac ON ac.lesson_id = l.course_item_id
WHERE l.content_type = 'ARTICLE';

INSERT INTO resources (id, title, resource_type, source_type, created_by)
SELECT l.course_item_id, ci.title, 'VIDEO', 'FILE', c.owner_id
FROM lessons l
         JOIN video_contents vc ON vc.lesson_id = l.course_item_id AND vc.media_id IS NOT NULL
         JOIN course_items ci ON ci.id = l.course_item_id
         JOIN course_sections cs ON cs.id = ci.section_id
         JOIN courses c ON c.id = cs.course_id
WHERE l.content_type = 'VIDEO';

INSERT INTO resources (id, title, resource_type, source_type, created_by)
SELECT l.course_item_id, ci.title, 'DOCUMENT', 'FILE', c.owner_id
FROM lessons l
         JOIN document_contents dc ON dc.lesson_id = l.course_item_id AND dc.media_id IS NOT NULL
         JOIN course_items ci ON ci.id = l.course_item_id
         JOIN course_sections cs ON cs.id = ci.section_id
         JOIN courses c ON c.id = cs.course_id
WHERE l.content_type = 'DOCUMENT';

INSERT INTO resource_files (resource_id, media_id, filename, mime_type, size_bytes)
SELECT l.course_item_id, vc.media_id, m.original_filename, m.mime_type, m.size_bytes
FROM lessons l
         JOIN video_contents vc ON vc.lesson_id = l.course_item_id AND vc.media_id IS NOT NULL
         JOIN media_objects m ON m.id = vc.media_id
WHERE l.content_type = 'VIDEO';

INSERT INTO resource_files (resource_id, media_id, filename, mime_type, size_bytes)
SELECT l.course_item_id, dc.media_id, m.original_filename, m.mime_type, m.size_bytes
FROM lessons l
         JOIN document_contents dc ON dc.lesson_id = l.course_item_id AND dc.media_id IS NOT NULL
         JOIN media_objects m ON m.id = dc.media_id
WHERE l.content_type = 'DOCUMENT';

UPDATE lessons l
SET primary_resource_id = l.course_item_id
WHERE EXISTS (SELECT 1 FROM resources r WHERE r.id = l.course_item_id);

-- A lesson with nothing in it: the type said video and no file was ever
-- attached, or the type was changed and the old row left behind. It answered
-- with an empty body and could not be played, read or downloaded. The course
-- item survives, so the author writes it again; the shell does not.
DELETE FROM lessons WHERE primary_resource_id IS NULL;

ALTER TABLE lessons ALTER COLUMN primary_resource_id SET NOT NULL;
CREATE INDEX idx_lessons_primary_resource ON lessons (primary_resource_id);

-- ---------------------------------------------------------------------------
-- 3. The old shape goes
-- ---------------------------------------------------------------------------

ALTER TABLE lessons DROP COLUMN content_type;

DROP TABLE video_contents;
DROP TABLE article_contents;
DROP TABLE document_contents;

-- ---------------------------------------------------------------------------
-- 4. A transcode job belongs to the file it encodes
-- ---------------------------------------------------------------------------
-- The job carried a lesson id, and was unique per (file, lesson). With the
-- rendition on the file, that is the wrong grain twice over: two lessons using
-- one upload queued two jobs and encoded the same bytes twice, and the result
-- has nowhere lesson-shaped to be written any more.

DELETE FROM transcode_jobs t
    USING transcode_jobs keep
WHERE keep.media_id = t.media_id
  AND (keep.created_at, keep.id) < (t.created_at, t.id);

ALTER TABLE transcode_jobs DROP CONSTRAINT uq_transcode_jobs_media_lesson;
ALTER TABLE transcode_jobs DROP COLUMN lesson_id;
ALTER TABLE transcode_jobs ADD CONSTRAINT uq_transcode_jobs_media UNIQUE (media_id);
