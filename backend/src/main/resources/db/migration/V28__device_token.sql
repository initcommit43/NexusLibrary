-- Where a push notification for a reader would be sent: one row per device they are signed in
-- on. Nothing sends yet; this is the store a sender will read.
--
-- The token is unique across the table, not per user. A token names a device install, not an
-- account, so when someone else signs in on the same phone the row moves to them rather than
-- leaving the previous owner's notifications going to a phone they have handed over.
CREATE TABLE device_token (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- The APNs or FCM registration token, as the platform handed it to the app.
    token         VARCHAR(512) NOT NULL UNIQUE,
    -- IOS or ANDROID: which service a sender has to address it through.
    platform      VARCHAR(16)  NOT NULL,
    registered_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Bumped by every registration, which the app repeats on each cold start. A row that
    -- stops being seen is a device that was wiped or uninstalled without signing out.
    last_seen_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_device_token_user ON device_token (user_id);
