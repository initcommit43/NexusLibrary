-- The banner a reader chose for the head of their profile, taken from a title in their own
-- library.
--
-- One row per reader, so the reader's id is the key: this is a single choice rather than a
-- collection, and absence means the profile draws no banner at all.
--
-- The image url is stored resolved. It lives inside the item's cached detail, which only the
-- source's own adapter can read, so keeping the answer here is what lets the profile paint
-- its head without a fetch. The item stays beside it so the banner still knows which title
-- it came from, and so the url can be resolved again if a source moves its images.
CREATE TABLE user_profile_banner (
    user_id           BIGINT      PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    trackable_item_id BIGINT      NOT NULL REFERENCES trackable_item (id) ON DELETE CASCADE,
    image_url         VARCHAR(500) NOT NULL,
    chosen_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
