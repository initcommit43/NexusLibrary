-- A profile picture the reader uploads, in place of a character picked from their library.
--
-- The picture row keeps its place and its framing; what it points at changes. A character
-- chosen before this keeps working until it is replaced, so its columns go nullable rather
-- than away, and a row holds exactly one of the two: a character's url or an upload's id.
--
-- The image itself lives in a table of its own. The picture row is read on every profile
-- paint and the bytes only when the image is asked for, and keeping them apart keeps a few
-- hundred kilobytes out of every read of the first. Never the uploaded file: these are the
-- bytes the server re-encoded, capped well above what it ever writes.
ALTER TABLE user_profile_picture
    ALTER COLUMN trackable_item_id DROP NOT NULL,
    ALTER COLUMN character_id DROP NOT NULL,
    ALTER COLUMN character_name DROP NOT NULL,
    ALTER COLUMN image_url DROP NOT NULL,
    ADD COLUMN upload_id UUID,
    ADD CONSTRAINT ck_profile_picture_one_source CHECK ((image_url IS NULL) <> (upload_id IS NULL));

CREATE TABLE user_profile_picture_upload (
    user_id BIGINT   PRIMARY KEY REFERENCES user_profile_picture (user_id) ON DELETE CASCADE,
    content BYTEA    NOT NULL,
    width   SMALLINT NOT NULL,
    height  SMALLINT NOT NULL,
    CONSTRAINT ck_profile_picture_upload_size CHECK (octet_length(content) <= 2097152)
);
