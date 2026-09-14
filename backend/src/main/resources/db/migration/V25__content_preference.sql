-- What each reader wants done about adult titles. One row per reader who changed either
-- setting; no row means the defaults, so an account nothing was ever written for stays safe.
CREATE TABLE user_content_preference (
    user_id    BIGINT      PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    -- Off by default: § 5 JMStV asks for a measure between minors and adult material, and a
    -- setting that started on would be none. The service only lets an 18+ account turn it on,
    -- and checks the age again on every read, because a row outlives the reason it was allowed.
    show_adult BOOLEAN     NOT NULL DEFAULT false,
    -- On by default, and its own switch: wanting adult titles in a library is not the same as
    -- wanting their covers drawn full size where someone else can see the screen.
    blur_adult BOOLEAN     NOT NULL DEFAULT true,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
