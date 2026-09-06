-- Records who uploaded a media object.
--
-- V1 modelled media purely as file metadata, which leaves no basis for deciding
-- who may complete an upload or fetch a download URL for it. Without an owner
-- any authenticated user could read any object key.
--
-- Nullable: objects created by the system (generated certificates, transcoded
-- renditions) have no human uploader, and ON DELETE SET NULL keeps the file
-- reachable after the uploader's account is removed.

ALTER TABLE media_objects
    ADD COLUMN created_by uuid REFERENCES users (id) ON DELETE SET NULL;

CREATE INDEX idx_media_objects_created_by ON media_objects (created_by);
