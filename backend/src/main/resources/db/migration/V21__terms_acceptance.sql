-- What a reader agreed to when they registered, and which version of it.
--
-- Art. 7(1) GDPR puts the burden of showing that consent was given on whoever relies on it,
-- and a checkbox that gates a form proves nothing once the request has been served. The
-- version travels with the timestamp because "they accepted the terms" is not a useful
-- answer when the terms have since changed: what matters is which text they saw.
--
-- Nullable, because every account created before this migration predates the question. NULL
-- means never recorded rather than refused, and is what a later re-consent prompt looks for.
ALTER TABLE app_user
    ADD COLUMN terms_accepted_at TIMESTAMPTZ,
    ADD COLUMN terms_version     VARCHAR(32);
