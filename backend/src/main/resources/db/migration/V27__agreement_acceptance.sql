-- Which version of which document a reader accepted, and on which platform.
--
-- V21 recorded consent as one timestamp and one version on app_user. That answers "did they
-- agree" but not "to which of our documents", and it cannot hold two answers at once. Three
-- things it could not express are now required:
--
--   1. The documents differ per platform. Terms and Privacy apply everywhere, an EULA is the
--      App Store's requirement of the native build, and cookie consent is meaningless to a
--      client that has no cookies.
--   2. Web and native acceptance stand separately. Agreeing in a browser is not agreeing on
--      a phone, and the native store listing is a different offer.
--   3. A document that changes has to be put back in front of the people who accepted the
--      older text. That needs the version per document, not per account.
--
-- History rather than a current-state row: Art. 7(1) GDPR puts the burden of showing consent
-- on whoever relies on it, and "they have accepted the current terms" is not an answer about
-- what someone agreed to two versions ago. Rows are only ever inserted.
CREATE TABLE agreement_acceptance (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- The document, as dev.nexus.auth.agreements.Agreement names it.
    document    VARCHAR(32) NOT NULL,
    -- WEB or NATIVE, from the client that sent the acceptance — never from a User-Agent.
    platform    VARCHAR(16) NOT NULL,
    -- The version of that document which was on screen. Dated, so it sorts and so it matches
    -- the "Last updated" line a reader would quote back at us.
    version     VARCHAR(32) NOT NULL,
    accepted_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Keeps history across versions while making a resent acceptance idempotent: a client
    -- that retries a POST it already made must not write a second row for the same text.
    CONSTRAINT uq_agreement_acceptance UNIQUE (user_id, document, platform, version)
);

-- The one read this table has on every sign-in: everything this account has accepted for the
-- platform it is calling from, which is then compared against the current versions.
CREATE INDEX idx_agreement_acceptance_user_platform ON agreement_acceptance (user_id, platform);

-- What V21 already recorded, carried over rather than asked again. The box those readers
-- ticked named the terms and the privacy policy, and it was only ever on the web form, so it
-- becomes exactly those two documents on WEB. Cookie consent was never asked and is not
-- invented here: it falls outstanding and is put to them at their next sign-in.
INSERT INTO agreement_acceptance (user_id, document, platform, version, accepted_at)
SELECT id, 'TERMS', 'WEB', terms_version, terms_accepted_at
  FROM app_user
 WHERE terms_accepted_at IS NOT NULL AND terms_version IS NOT NULL;

INSERT INTO agreement_acceptance (user_id, document, platform, version, accepted_at)
SELECT id, 'PRIVACY', 'WEB', terms_version, terms_accepted_at
  FROM app_user
 WHERE terms_accepted_at IS NOT NULL AND terms_version IS NOT NULL;
