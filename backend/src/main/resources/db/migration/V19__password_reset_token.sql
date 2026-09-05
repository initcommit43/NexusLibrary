-- The password reset links that have been handed out and not yet spent.
--
-- Unlike refresh_token, which lists only the `jti` naming a token its owner holds, the value
-- here IS the credential: whoever presents it sets the account's password without knowing the
-- old one. So the link is never stored — only its SHA-256 — and a row read out of this table
-- is worth nothing. The token has 256 bits of entropy behind it, which is what makes a plain
-- digest enough; a password would need bcrypt, a random 32 bytes does not.
CREATE TABLE password_reset_token (
    id         BIGSERIAL    PRIMARY KEY,
    -- SHA-256 of the token in the link, hex. Unique so a digest can only ever name one link.
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Thirty minutes after it was asked for. A reset link is read within a minute or two of
    -- arriving; anything longer is a live credential sitting in an inbox for no reason.
    expires_at TIMESTAMPTZ  NOT NULL,
    -- Null until the link is spent. Set by the reset itself, and by asking for another one:
    -- only the newest link works, so a forwarded or resent older mail is already dead.
    used_at    TIMESTAMPTZ
);

-- Asking for a link retires the outstanding ones; the sweep reads what has expired.
CREATE INDEX idx_password_reset_user_live ON password_reset_token (user_id) WHERE used_at IS NULL;
CREATE INDEX idx_password_reset_expires ON password_reset_token (expires_at);
