-- The refresh tokens still allowed.
--
-- A signed JWT cannot be recalled, which is the problem this solves: signing out cleared the
-- cookie holding the token but left the token itself working, so a thirty-day credential
-- stayed valid for its full life however it had been lost. A phone makes that concrete —
-- the token sits in the device keychain rather than in a cookie the browser alone can read.
--
-- Only refresh tokens are listed. Access tokens stay stateless and short so an authenticated
-- request costs no query; their fifteen minutes is how long a revoked session keeps working.
CREATE TABLE refresh_token (
    id         BIGSERIAL   PRIMARY KEY,
    -- The `jti` claim of the token this row admits. A name, not the credential: the token is
    -- held by its owner, so a row read out of this table is not a way in.
    jti        UUID        NOT NULL UNIQUE,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- WEB or NATIVE. Which of the two ways the token was handed over, and so where it lives:
    -- an httpOnly cookie the browser keeps, or a phone's keychain.
    client     VARCHAR(16) NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    -- Null while the session is live. Set by signing out, by signing every session out, and
    -- by each refresh, which retires the token it replaces.
    revoked_at TIMESTAMPTZ
);

-- Signing every session out reads a user's live rows; the sweep reads what has expired.
CREATE INDEX idx_refresh_token_user_live ON refresh_token (user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_token_expires ON refresh_token (expires_at);
