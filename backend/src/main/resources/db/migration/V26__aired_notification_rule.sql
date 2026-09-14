-- Aired-episode notifications follow one rule now: every episode for a title on the Watching
-- list, only the premiere for a title on any other list. The sweep and the AniList import used
-- to write one for every episode of everything on a shelf, and those rows would otherwise sit
-- in the feed for good.
--
-- Matched on subject rather than payload: both writers always set "episode:N" there, and it is
-- the column the uniqueness rule already stands on. A reader who no longer keeps the title has
-- nothing left to be told about it.
DELETE FROM notification n
 WHERE n.type = 'EPISODE_AIRED'
   AND n.subject <> 'episode:1'
   AND NOT EXISTS (
        SELECT 1
          FROM user_entry e
         WHERE e.user_id = n.user_id
           AND e.trackable_item_id = n.trackable_item_id
           AND e.status = 'IN_PROGRESS');

DELETE FROM notification n
 WHERE n.type = 'EPISODE_AIRED'
   AND n.subject = 'episode:1'
   AND NOT EXISTS (
        SELECT 1
          FROM user_entry e
         WHERE e.user_id = n.user_id
           AND e.trackable_item_id = n.trackable_item_id);
