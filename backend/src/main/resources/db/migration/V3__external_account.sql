-- A user's link to an external service they import from.
CREATE TABLE external_account (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider         VARCHAR(16) NOT NULL,
    -- The account's id at the provider: a SteamID64, an AniList user id, and so on.
    external_user_id VARCHAR(64) NOT NULL,
    -- Encrypted at rest. Null for providers that issue no token: Steam OpenID proves
    -- identity only, so a Steam link stores an id and nothing secret.
    access_token     TEXT,
    refresh_token    TEXT,
    token_expires_at TIMESTAMPTZ,
    connected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_synced_at   TIMESTAMPTZ,
    CONSTRAINT uq_external_account_user_provider UNIQUE (user_id, provider)
);

CREATE INDEX idx_external_account_user ON external_account (user_id);
