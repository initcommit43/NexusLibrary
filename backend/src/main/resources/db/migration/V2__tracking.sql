-- The globally shared item cache: one row per unique external item, reused by every user.
CREATE TABLE trackable_item (
    id           BIGSERIAL PRIMARY KEY,
    media_type   VARCHAR(16)  NOT NULL,
    source       VARCHAR(16)  NOT NULL,
    external_id  VARCHAR(64)  NOT NULL,
    title        VARCHAR(500) NOT NULL,
    cover_url    VARCHAR(500),
    release_date DATE,
    item_state   VARCHAR(16)  NOT NULL,
    metadata     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    cached_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    refreshed_at TIMESTAMPTZ,
    CONSTRAINT uq_trackable_item_source_external UNIQUE (source, external_id)
);

-- A user's relationship to a cached item.
CREATE TABLE user_entry (
    id                BIGSERIAL PRIMARY KEY,
    -- Cascade makes the DSGVO "delete my data" requirement a single statement.
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    trackable_item_id BIGINT      NOT NULL REFERENCES trackable_item (id),
    status            VARCHAR(16) NOT NULL,
    rating            SMALLINT    CHECK (rating BETWEEN 0 AND 100),
    progress_current  INT,
    -- Null is valid: playtime has no fixed maximum.
    progress_max      INT,
    progress_unit     VARCHAR(16),
    progress_extra    JSONB,
    started_at        DATE,
    finished_at       DATE,
    favorite          BOOLEAN     NOT NULL DEFAULT false,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_entry_user_item UNIQUE (user_id, trackable_item_id)
);

CREATE INDEX idx_user_entry_user ON user_entry (user_id);
