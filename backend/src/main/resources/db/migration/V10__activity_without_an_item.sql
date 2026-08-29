-- An import or a sync is one event about many titles, so it belongs to no single one.
--
-- Everything recorded until now was a thing done to one title by hand, and the column has
-- carried a not-null since. A run says "771 anime arrived from AniList" and names its titles
-- in the payload instead; the feed reads the title and cover off the item where there is one
-- and off the payload where there is not.
ALTER TABLE activity ALTER COLUMN trackable_item_id DROP NOT NULL;
