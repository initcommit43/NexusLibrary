-- Two favourite rows sharing one band across the page.
--
-- Stored against the second of the pair, as "this one sits beside the one before it",
-- rather than as a band id shared by both. The rows already carry an order, so the row
-- before is a fact the table has: a separate id would be a second way of saying the same
-- thing and a second way for the two to disagree.
--
-- False for every row that stands alone, which is every row until a reader drags one onto
-- another, so nothing needs writing for a profile that never does.
ALTER TABLE user_favourite_row
    ADD COLUMN shares_lane BOOLEAN NOT NULL DEFAULT false;
