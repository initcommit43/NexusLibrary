-- Where an entry came from: an import, or the user adding it by hand.
--
-- Distinct from the Steam appid recorded on trackable_item, which is a fact about the game
-- and identical for everyone. This is a fact about one person's copy of it.
ALTER TABLE user_entry ADD COLUMN imported_from VARCHAR(16);
