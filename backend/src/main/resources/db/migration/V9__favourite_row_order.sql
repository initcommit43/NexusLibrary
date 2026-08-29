-- The order a reader arranged their favourite rows into, one row per media type.
--
-- Kept by media type rather than by the rows that happen to have something in them: a row
-- with no favourites left is not drawn, and storing it this way is what lets it come back
-- where it was put rather than at the end.
--
-- Absence means the app's own order, so a reader who never rearranges anything costs no
-- rows, and a media type added later arrives after the ones already placed.
CREATE TABLE user_favourite_row (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_type VARCHAR(16) NOT NULL,
    sort_order INTEGER     NOT NULL,
    UNIQUE (user_id, media_type)
);

CREATE INDEX idx_user_favourite_row_user ON user_favourite_row (user_id);
