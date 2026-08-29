package dev.nexus.modules.anime;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.domain.MediaType;
import java.time.LocalDate;
import java.util.List;

/**
 * What the anime and manga browse pages offer, and the AniList query behind each row.
 *
 * <p>The two media types get different shelves rather than one set with some rows empty.
 * Anime is scheduled in broadcast seasons and read that way — what is airing now, what starts
 * next quarter — and manga simply is not; a "popular this season" row for manga would be a
 * question nobody asks. Light novels get a row of their own on the manga side, since AniList
 * files them under the same type and they are a different thing to read.
 */
final class AniListShelves {

    /** One shelf: what a reader sees, and the AniList arguments that produce it. */
    record Definition(
            String id, String label, String sort, boolean seasonal, boolean nextSeason, String status, String format) {

        static Definition of(String id, String label, String sort) {
            return new Definition(id, label, sort, false, false, null, null);
        }

        Definition inCurrentSeason() {
            return new Definition(id, label, sort, true, false, status, format);
        }

        Definition inNextSeason() {
            return new Definition(id, label, sort, true, true, "NOT_YET_RELEASED", format);
        }

        Definition ofFormat(String mediaFormat) {
            return new Definition(id, label, sort, seasonal, nextSeason, status, mediaFormat);
        }

        /** The season argument for this shelf, or null where the shelf is not seasonal. */
        String season(LocalDate today) {
            if (!seasonal) {
                return null;
            }
            AniListSeason season = AniListSeason.of(today);
            return (nextSeason ? season.next() : season).name();
        }

        Integer seasonYear(LocalDate today) {
            if (!seasonal) {
                return null;
            }
            return nextSeason ? AniListSeason.of(today).nextYear(today.getYear()) : today.getYear();
        }
    }

    private static final String TRENDING = "TRENDING_DESC";
    private static final String POPULARITY = "POPULARITY_DESC";
    private static final String SCORE = "SCORE_DESC";

    /** AniList numbers its records as they arrive, so its newest ids are its newest entries. */
    private static final String NEWEST = "ID_DESC";

    private static final List<Definition> ANIME = List.of(
            Definition.of("trending", "Trending now", TRENDING),
            Definition.of("this-season", "Popular this season", POPULARITY).inCurrentSeason(),
            Definition.of("next-season", "Upcoming next season", POPULARITY).inNextSeason(),
            Definition.of("popular", "All time popular", POPULARITY),
            Definition.of("top", "Top 100 anime", SCORE),
            Definition.of("newly-added", "Newly added", NEWEST));

    private static final List<Definition> MANGA = List.of(
            Definition.of("trending", "Trending now", TRENDING),
            Definition.of("popular", "All time popular", POPULARITY),
            Definition.of("light-novels", "Popular light novels", POPULARITY).ofFormat("NOVEL"),
            Definition.of("top", "Top 100 manga", SCORE),
            Definition.of("newly-added", "Newly added", NEWEST));

    private AniListShelves() {}

    static List<Definition> definitionsFor(MediaType mediaType) {
        return mediaType == MediaType.MANGA ? MANGA : ANIME;
    }

    static List<BrowseShelf> shelvesFor(MediaType mediaType) {
        return definitionsFor(mediaType).stream()
                .map(definition -> new BrowseShelf(definition.id(), definition.label()))
                .toList();
    }

    /** Null for an id no shelf claims, which is a bug rather than something to query for. */
    static Definition find(MediaType mediaType, String shelfId) {
        return definitionsFor(mediaType).stream()
                .filter(definition -> definition.id().equals(shelfId))
                .findFirst()
                .orElse(null);
    }
}
