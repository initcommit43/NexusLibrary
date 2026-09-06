-- The character a reader chose to stand for them, taken from a title in their own library.
--
-- Shaped like user_profile_banner beside it and for the same reasons: one row per reader,
-- keyed by the reader, and absence means the profile falls back to a plain icon.
--
-- The character's own id and name are kept next to the resolved url. The id is what the
-- choice actually was, so the picture can be resolved again if a source moves its images;
-- the name is what the profile credits, and reading it back out of a cached detail on every
-- paint would trade a column for a JSON walk.
--
-- Framing ships here rather than as a follow-up: the banner learned it in V13 only after the
-- fact, and there is no reason to repeat that in two steps.
CREATE TABLE user_profile_picture (
    user_id           BIGINT       PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    trackable_item_id BIGINT       NOT NULL REFERENCES trackable_item (id) ON DELETE CASCADE,
    character_id      VARCHAR(64)  NOT NULL,
    character_name    VARCHAR(255) NOT NULL,
    image_url         VARCHAR(500) NOT NULL,
    chosen_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    focus_x           SMALLINT     NOT NULL DEFAULT 50,
    focus_y           SMALLINT     NOT NULL DEFAULT 50,
    zoom              SMALLINT     NOT NULL DEFAULT 100
);
