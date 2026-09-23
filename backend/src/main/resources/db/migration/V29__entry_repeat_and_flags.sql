-- Three things a reader can say about an entry that the model had no room for.
--
-- repeat_count: how many times they have gone through it again after the first time. A
-- rewatch for film and anime, a replay for a game, a reread for a book; one column, since
-- only the word differs.
--
-- is_private and hidden_from_status_lists: stored and returned, nothing more. Nothing in the
-- app is visible to anyone but its owner yet, so there is nothing for "private" to hide from;
-- the flags are kept now so a client can set them and a later feature can honour them.
ALTER TABLE user_entry
    ADD COLUMN repeat_count             INTEGER NOT NULL DEFAULT 0 CHECK (repeat_count >= 0),
    ADD COLUMN is_private               BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN hidden_from_status_lists BOOLEAN NOT NULL DEFAULT false;
