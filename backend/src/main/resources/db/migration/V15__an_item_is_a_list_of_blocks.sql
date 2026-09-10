-- ============================================================================
-- A lesson stops being one material and becomes an ordered list of them
-- ============================================================================
-- `item_resources` has carried ordering and relationship types since V1, and
-- was used only for extras hanging off a lesson - a reading list beside the
-- video. The lesson's own material lived somewhere else entirely, in
-- `lessons.primary_resource_id`, which meant an item had two content
-- mechanisms and only one of them could be ordered.
--
-- That is the wrong shape for what a lesson actually is. "Watch this, then
-- read the notes, then download the slides" is one lesson, and saying it
-- previously took three course items - splitting one lesson's progress across
-- three rows of the curriculum for a reason no student would recognise.
--
-- So the list becomes the whole story. Every existing lesson's material joins
-- `item_resources` at the front, ahead of whatever was already attached to it,
-- and the pointer that used to hold it becomes optional.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Make room at the front
-- ---------------------------------------------------------------------------
-- Existing attachments were positioned relative to each other with nothing in
-- front of them, because the lesson's own material was never in this table.
-- It is about to be, and it goes first: it is what the lesson was.

UPDATE item_resources ir
SET position = position + 1
WHERE EXISTS (
    SELECT 1 FROM lessons l
    WHERE l.course_item_id = ir.course_item_id
      AND l.primary_resource_id IS NOT NULL
);

-- ---------------------------------------------------------------------------
-- 2. The lesson's own material becomes its first block
-- ---------------------------------------------------------------------------
-- ON CONFLICT because a lesson may already list its own primary resource as an
-- attachment - nothing ever stopped an author attaching it twice. Where that
-- happened the row already exists, and step 1 has already put it in front.

INSERT INTO item_resources (course_item_id, resource_id, relationship_type, position)
SELECT l.course_item_id, l.primary_resource_id, 'RESOURCE', 0
FROM lessons l
WHERE l.primary_resource_id IS NOT NULL
ON CONFLICT (course_item_id, resource_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. The pointer becomes optional
-- ---------------------------------------------------------------------------
-- Kept rather than dropped, and kept meaning exactly one thing: which block a
-- lesson-level request is about. `/lesson/content-url` and `/lesson/stream.m3u8`
-- ask for "this lesson's file" without naming a block, and DURATION completion
-- describes one runtime rather than the sum of everything on the page.
--
-- Null from here on means a lesson assembled entirely from blocks, which the
-- lesson-level endpoints answer by resolving the first block that can satisfy
-- them. Existing lessons keep their pointer, so nothing that works today stops.

ALTER TABLE lessons ALTER COLUMN primary_resource_id DROP NOT NULL;

COMMENT ON COLUMN lessons.primary_resource_id IS
    'Which block a lesson-level request means, or null to resolve the first that fits. The blocks themselves are item_resources.';
