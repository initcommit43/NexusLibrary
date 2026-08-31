-- How far a walk of somebody else's stream has got.
--
-- A history goes back years, and one run of it is capped so that a single press cannot spend
-- the shared API budget for a quarter of an hour. What makes the cap bearable is this table:
-- the next run starts where the last one stopped rather than at the top, so pressing the
-- button again finishes the job instead of redoing it.
--
-- Also what makes a failure cheap. A run that dies halfway through leaves its place here, so
-- nothing already fetched has to be fetched twice.
CREATE TABLE sync_progress (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider   VARCHAR(16) NOT NULL,
    -- Which walk: a reader's activity and their notifications are separate streams.
    kind       VARCHAR(24) NOT NULL,
    -- The page the next run should ask for. Back to 1 once the stream has been walked out,
    -- because the next run after that is looking for what is new rather than what is old.
    next_page  INT         NOT NULL DEFAULT 1,
    -- Whether the walk has reached the end of the stream at least once.
    complete   BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (user_id, provider, kind)
);
