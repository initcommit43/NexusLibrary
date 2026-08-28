-- Which modules a reader has switched off. Stored as the media types behind them: a module
-- is the client's grouping of these, and the server has never needed to know that grouping.
--
-- Absence means enabled, so a new reader has everything on without a row being written for
-- them, and a module added later is on for everyone by default.
CREATE TABLE user_disabled_module (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    media_type VARCHAR(16) NOT NULL,
    UNIQUE (user_id, media_type)
);

CREATE INDEX idx_user_disabled_module_user ON user_disabled_module (user_id);
