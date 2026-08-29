-- Where a favourite sits in the reader's own arrangement of them.
--
-- Null means unplaced, which is every favourite until one is dragged: a reader who never
-- rearranges anything keeps the default order without a rank being written for them, and a
-- newly marked favourite joins the end rather than displacing what is already arranged.
ALTER TABLE user_entry ADD COLUMN favorite_rank INTEGER;
