-- What a user did, in order. Payload carries the shape each type needs rather than
-- a column per type: an activity is written once and never queried by its innards.
CREATE TABLE activity (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    trackable_item_id BIGINT      NOT NULL REFERENCES trackable_item (id),
    type              VARCHAR(16) NOT NULL,
    payload           JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The feed is always "this user, newest first", so index for exactly that.
CREATE INDEX idx_activity_user_created ON activity (user_id, created_at DESC);

CREATE TABLE review (
    id               BIGSERIAL PRIMARY KEY,
    -- One review per entry, and the entry already carries the user, so ownership is
    -- inherited rather than duplicated.
    user_entry_id    BIGINT      NOT NULL UNIQUE REFERENCES user_entry (id) ON DELETE CASCADE,
    body             TEXT        NOT NULL,
    contains_spoilers BOOLEAN    NOT NULL DEFAULT false,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
