-- V30 moved every cached AniList cover from the medium path to the large one, but only covers
-- uploaded in AniList's newer scheme exist at both. Those are named bx<id>-… or nx<id>-…; an
-- older upload (a bare <id>.jpg, or b<id>-…) exists at the medium size alone, and the API
-- itself hands the medium URL back as its extraLarge. Its rewritten URL is a 404, and since a
-- released title is never refreshed it would stay one, so it goes back to where it was.

UPDATE trackable_item
SET cover_url = replace(cover_url, '/cover/large/', '/cover/medium/')
WHERE source = 'ANILIST'
  AND cover_url LIKE '%/cover/large/%'
  AND cover_url !~ '/cover/large/(bx|nx)';
