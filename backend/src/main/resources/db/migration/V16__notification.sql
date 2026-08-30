-- Something that happened to a title a reader keeps, that they would want telling about: an
-- episode airing tonight, a second season appearing on the source.
--
-- Kept apart from `activity`, which is what the reader did, and from `provider_activity`,
-- which is what they did somewhere else. This is what happened while they were not looking.
CREATE TABLE notification (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    trackable_item_id BIGINT      NOT NULL REFERENCES trackable_item (id) ON DELETE CASCADE,
    type              VARCHAR(24) NOT NULL,
    -- What exactly happened, as a short stable key: "episode:12", "added". It is what makes
    -- telling someone twice impossible, since the detector runs again every quarter hour and
    -- an episode stays aired.
    subject           VARCHAR(64) NOT NULL,
    payload           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Null until it has been seen, which is the whole of what makes one new.
    read_at           TIMESTAMPTZ,

    UNIQUE (user_id, trackable_item_id, type, subject)
);

-- The list is always "this reader, newest first", and the count is "this reader, unread".
CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
CREATE INDEX idx_notification_user_unread ON notification (user_id) WHERE read_at IS NULL;
