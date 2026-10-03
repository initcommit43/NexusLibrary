-- Covers already cached at the sizes the adapters no longer ask for, moved to the ones they do.
--
-- IGDB's big cover is 264 pixels wide and AniList's large some 230, both under the 240 a
-- title's page draws a cover at. The same image sits at a doubled size on each CDN under a
-- path that differs only in its size segment, so the stored URLs are rewritten rather than
-- left to be fetched again one title at a time as each falls due for a refresh.

UPDATE trackable_item
SET cover_url = replace(cover_url, '/upload/t_cover_big/', '/upload/t_cover_big_2x/')
WHERE source = 'IGDB'
  AND cover_url LIKE '%/upload/t_cover_big/%';

UPDATE trackable_item
SET cover_url = replace(cover_url, '/cover/medium/', '/cover/large/')
WHERE source = 'ANILIST'
  AND cover_url LIKE '%/cover/medium/%';
