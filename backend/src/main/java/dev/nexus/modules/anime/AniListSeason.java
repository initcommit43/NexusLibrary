package dev.nexus.modules.anime;

import java.time.LocalDate;

/**
 * AniList's four broadcast seasons, and the arithmetic for asking about the next one.
 *
 * <p>Anime is scheduled in quarters and talked about that way — "the spring season" is a real
 * thing a reader plans around, which is why a seasonal shelf is worth having at all. Manga has
 * no equivalent, which is why the manga shelves are a different set rather than the same set
 * with the seasonal rows returning nothing.
 */
enum AniListSeason {
    WINTER,
    SPRING,
    SUMMER,
    FALL;

    /** The season a date falls in. AniList counts January to March as WINTER. */
    static AniListSeason of(LocalDate date) {
        return switch ((date.getMonthValue() - 1) / 3) {
            case 0 -> WINTER;
            case 1 -> SPRING;
            case 2 -> SUMMER;
            default -> FALL;
        };
    }

    AniListSeason next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** The year the next season falls in — the following one, when this season is FALL. */
    int nextYear(int year) {
        return this == FALL ? year + 1 : year;
    }
}
