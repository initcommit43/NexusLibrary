-- Ask AniList's titles again, now that the list fields carry the next episode.
--
-- Adding a field to what we fetch does not make what we already hold wrong, so nothing
-- treats those copies as stale: an airing anime refreshed an hour ago would sit without a
-- countdown until its day was up, and one refreshed nightly might never show one at all.
--
-- Ageing the timestamp is what marks them worth re-reading. A null would not do it — that
-- means "inserted moments ago" to the staleness policy, which is the opposite. The re-read
-- costs one batched request per fifty titles and happens behind the next page that lists them.
UPDATE trackable_item
SET refreshed_at = now() - INTERVAL '30 days'
WHERE source = 'ANILIST'
  AND item_state = 'ONGOING';
