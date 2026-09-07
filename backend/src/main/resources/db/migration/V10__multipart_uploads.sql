-- Resumable uploads.
--
-- A presigned PUT is one HTTP request, which fails a lecture-sized file twice
-- over: the signature expires after 15 minutes while a 4GB upload on an
-- ordinary connection takes closer to an hour, and when anything interrupts it
-- there is nowhere for the transferred bytes to live, so the client starts from
-- zero. S3 caps a single PUT at 5GB regardless.
--
-- Multipart fixes both. Each part gets its own short-lived URL, and parts that
-- landed are held by storage between requests - which is what makes resuming
-- possible at all: the client asks which parts arrived and sends only the rest.
--
-- The upload_id is what the server has to remember. A browser that closed
-- cannot reconstruct it, so without this column a half-finished upload is
-- unresumable no matter what storage is still holding.

ALTER TABLE media_objects
    ADD COLUMN upload_id varchar(255);

COMMENT ON COLUMN media_objects.upload_id IS
    'S3 multipart upload id, set while a resumable upload is in flight and '
    'cleared once it completes. NULL for single-shot uploads.';
