package dev.nexus.modules.anime;

import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FilterField.FilterOption;
import dev.nexus.core.domain.MediaType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * What a reader can narrow anime and manga down to, and the AniList values behind each choice.
 *
 * <p>The two media types get different controls rather than one set with some inapplicable.
 * Anime is scheduled in broadcast seasons and manga is not, so manga has no season control at
 * all; the formats and the words for a status differ for the same reason a shelf does.
 */
final class AniListFilters {

    /** The year AniList's catalogue thins out below; older titles are found by name. */
    private static final int EARLIEST_YEAR = 1940;

    /**
     * AniList's genres, used when the live list cannot be fetched. Short and slow-moving —
     * it has not changed in years — so a stale copy is a far better answer than an empty
     * control that makes the filter look broken.
     */
    static final List<String> FALLBACK_GENRES = List.of(
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Horror",
            "Mahou Shoujo",
            "Mecha",
            "Music",
            "Mystery",
            "Psychological",
            "Romance",
            "Sci-Fi",
            "Slice of Life",
            "Sports",
            "Supernatural",
            "Thriller");

    private static final List<FilterOption> SEASONS = List.of(
            new FilterOption("WINTER", "Winter"),
            new FilterOption("SPRING", "Spring"),
            new FilterOption("SUMMER", "Summer"),
            new FilterOption("FALL", "Fall"));

    private static final List<FilterOption> ANIME_FORMATS = List.of(
            new FilterOption("TV", "TV Show"),
            new FilterOption("TV_SHORT", "TV Short"),
            new FilterOption("MOVIE", "Movie"),
            new FilterOption("SPECIAL", "Special"),
            new FilterOption("OVA", "OVA"),
            new FilterOption("ONA", "ONA"),
            new FilterOption("MUSIC", "Music"));

    private static final List<FilterOption> MANGA_FORMATS = List.of(
            new FilterOption("MANGA", "Manga"),
            new FilterOption("NOVEL", "Light Novel"),
            new FilterOption("ONE_SHOT", "One Shot"));

    private static final List<FilterOption> ANIME_STATUS = List.of(
            new FilterOption("RELEASING", "Airing"),
            new FilterOption("FINISHED", "Finished"),
            new FilterOption("NOT_YET_RELEASED", "Not Yet Aired"),
            new FilterOption("CANCELLED", "Cancelled"),
            new FilterOption("HIATUS", "Hiatus"));

    private static final List<FilterOption> MANGA_STATUS = List.of(
            new FilterOption("RELEASING", "Releasing"),
            new FilterOption("FINISHED", "Finished"),
            new FilterOption("NOT_YET_RELEASED", "Not Yet Published"),
            new FilterOption("CANCELLED", "Cancelled"),
            new FilterOption("HIATUS", "Hiatus"));

    /** What a chosen value is marked with, so the adapter can hand it to the right argument. */
    static final String GENRE = "genre:";

    static final String TAG = "tag:";

    private AniListFilters() {}

    static List<FilterField> forMediaType(
            MediaType mediaType, List<String> genres, List<String> tags, LocalDate today) {
        boolean anime = mediaType == MediaType.ANIME;
        List<FilterField> fields = new ArrayList<>();

        fields.add(FilterField.text("q", "Search"));
        // One box holding both, as AniList's own is: a reader narrowing by "Isekai" is not
        // asking a different kind of question from one narrowing by "Fantasy", and two boxes
        // means knowing which of the two a word is before you can look for it.
        fields.add(FilterField.multi("genres", "Genres & Tags", genresAndTags(genres, tags)));
        fields.add(FilterField.select("year", "Year", years(today)));
        if (anime) {
            fields.add(FilterField.select("season", "Season", SEASONS));
        }
        fields.add(FilterField.select("format", "Format", anime ? ANIME_FORMATS : MANGA_FORMATS));
        fields.add(FilterField.select(
                "status", anime ? "Airing Status" : "Publishing Status", anime ? ANIME_STATUS : MANGA_STATUS));

        return List.copyOf(fields);
    }

    /**
     * Both lists in one, each value saying which side it came from.
     *
     * <p>The label is the bare word, because that is what a reader picks; the value carries
     * the prefix, because AniList takes genres and tags as different arguments and there is
     * no telling them apart afterwards otherwise — "Mecha" is both.
     */
    private static List<FilterOption> genresAndTags(List<String> genres, List<String> tags) {
        List<FilterOption> options = new ArrayList<>(prefixed(GENRE, genres));
        options.addAll(prefixed(TAG, tags));
        return List.copyOf(options);
    }

    private static List<FilterOption> prefixed(String prefix, List<String> values) {
        return values.stream()
                .map(value -> new FilterOption(prefix + value, value))
                .toList();
    }

    /**
     * Newest first, and one year ahead of today: anime is announced a season before it airs,
     * so the year a reader is most likely to want is sometimes not yet the current one.
     */
    private static List<FilterOption> years(LocalDate today) {
        return IntStream.rangeClosed(EARLIEST_YEAR, today.getYear() + 1)
                .map(year -> EARLIEST_YEAR + today.getYear() + 1 - year)
                .mapToObj(year -> new FilterOption(String.valueOf(year), String.valueOf(year)))
                .toList();
    }
}
