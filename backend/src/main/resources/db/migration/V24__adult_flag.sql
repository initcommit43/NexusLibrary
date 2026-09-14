-- Whether a cached title is adult material, as its source files it: AniList's isAdult, TMDB's
-- adult, IGDB's Erotic theme. A fact about the title, so it lives on the shared item.
ALTER TABLE trackable_item ADD COLUMN adult BOOLEAN NOT NULL DEFAULT false;

-- The default is not right for every row already here. AniList search, MAL imports and IGDB
-- never filtered adult titles out, so some cached rows are adult. A RELEASED item is never
-- refreshed, so for most of them this backfill is the only way the flag ever gets set.
--
-- AniList files every isAdult title under its Hentai genre, which the cache keeps. IGDB's theme
-- names are only cached for games whose detail page someone has opened. TMDB keeps no trace of
-- its flag in the metadata, so TMDB rows cannot be backfilled here and stay false until they
-- are fetched again.
UPDATE trackable_item
SET adult = true
WHERE (source = 'ANILIST' AND metadata->'genres' ? 'Hentai')
   OR (source = 'IGDB' AND metadata->'detail'->'themes' ? 'Erotic');
