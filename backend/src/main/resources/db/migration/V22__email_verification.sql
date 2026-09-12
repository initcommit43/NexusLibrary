-- Proof that the address on an account reaches the person who opened it.
--
-- Registration already refuses malformed, throwaway and undeliverable addresses, but none of
-- that shows the address belongs to whoever typed it. Only a link arriving in the inbox does.

-- Null until the link is followed. Nullable rather than a boolean default: "when" answers
-- support questions a "whether" cannot, and every account made before this migration is
-- unverified rather than silently grandfathered in as verified.
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts that already existed keep working. They were made when nothing asked, so refusing
-- them now would lock people out of their own libraries over a rule introduced after the fact.
UPDATE app_user SET email_verified_at = created_at;

CREATE TABLE email_verification_token (
    id         BIGSERIAL    PRIMARY KEY,
    -- SHA-256 of the token in the link, hex. The link itself is never stored: a leaked table
    -- must not be a set of working links. Unique so a digest can only ever name one.
    token_hash VARCHAR(64)  NOT NULL UNIQUE,
    user_id    BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Longer than a reset link's half hour: this one is read when someone gets round to it,
    -- and it grants nothing on its own beyond confirming an address already on the account.
    expires_at TIMESTAMPTZ  NOT NULL,
    -- Null until followed. Asking for another link spends the old one, so only the newest
    -- works and a forwarded older mail is already dead.
    used_at    TIMESTAMPTZ
);

-- The two reads this table has: find a user's live link, and sweep the expired ones.
CREATE INDEX idx_email_verification_user_live
    ON email_verification_token (user_id) WHERE used_at IS NULL;
CREATE INDEX idx_email_verification_expires ON email_verification_token (expires_at);
