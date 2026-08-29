-- Names that differ only in case are the same name to a person reading them.
--
-- Both columns already carry a UNIQUE constraint, but it compares byte for byte, so "Demo"
-- and "demo" were two accounts. Nothing signs in as the wrong one — the lookup is exact —
-- but either could be taken for the other by anyone reading a list, and a name someone
-- believes is theirs could be claimed beside it.
CREATE UNIQUE INDEX uq_app_user_username_lower ON app_user (LOWER(username));

-- Addresses are folded before they are stored, so this holds a rule that already holds
-- rather than changing one. It is here so the database says so too.
CREATE UNIQUE INDEX uq_app_user_email_lower ON app_user (LOWER(email));
