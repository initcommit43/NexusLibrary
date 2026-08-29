-- How the chosen image sits inside the strip that shows it.
--
-- The two are rarely the same shape: a screenshot is sixteen by nine and the head of a
-- profile is far wider than it is tall, so a cover crop of one takes a band out of the
-- middle and throws away whatever the reader picked it for. These say which band.
--
-- Percentages of the image rather than pixels, because the strip's own width follows the
-- window and a pixel offset would mean something different at every size. Zoom is a
-- hundredth, so the whole framing is integers and nothing here rounds differently in two
-- languages. The defaults are the plain cover crop every banner starts as.
ALTER TABLE user_profile_banner
    ADD COLUMN focus_x SMALLINT NOT NULL DEFAULT 50,
    ADD COLUMN focus_y SMALLINT NOT NULL DEFAULT 50,
    ADD COLUMN zoom    SMALLINT NOT NULL DEFAULT 100;
