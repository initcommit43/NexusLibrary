-- A provider's own record of what a reader did, day by day: an episode watched, a chapter
-- read, a series put on hold.
--
-- Kept apart from `activity`, which is what was done inside this app and is read as a feed.
-- This is imported history, it arrives in thousands of rows at a time, and it is only ever
-- read as a tally per day for the map on a profile. A start date and a finish date are two
-- days out of the hundreds a reader actually had.
CREATE TABLE provider_activity (
    id                BIGSERIAL   PRIMARY KEY,
    user_id           BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider          VARCHAR(16) NOT NULL,
    -- The provider's own id for the event, which is what lets an import run twice and add
    -- nothing the second time.
    external_id       VARCHAR(32) NOT NULL,
    trackable_item_id BIGINT      NOT NULL REFERENCES trackable_item (id) ON DELETE CASCADE,
    happened_on       DATE        NOT NULL,
    -- What the event said. The map needs only the day; these are what a feed would read.
    status            VARCHAR(32),
    progress          VARCHAR(32),
    imported_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (user_id, provider, external_id)
);

-- The map asks for one user's days since a date, and nothing else ever asks anything.
CREATE INDEX idx_provider_activity_user_day ON provider_activity (user_id, happened_on);
