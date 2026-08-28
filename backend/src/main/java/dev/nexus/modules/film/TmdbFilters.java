package dev.nexus.modules.film;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FilterField.FilterOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * What a reader can narrow the films and shows browse page down to, and the TMDB query it
 * becomes.
 *
 * <p>Movies and shows are filtered by the same five things, but not with the same words: the
 * genre lists differ, and TMDB names the release year differently for each.
 */
final class TmdbFilters {

    /** Film goes back further than this, but TMDB's coverage of it does not. */
    private static final int EARLIEST_YEAR = 1900;

    /** A floor rather than a band: nobody looks for films rated between six and seven. */
    private static final List<FilterOption> RATINGS = List.of(
            new FilterOption("5", "5+"),
            new FilterOption("6", "6+"),
            new FilterOption("7", "7+"),
            new FilterOption("8", "8+"),
            new FilterOption("9", "9+"));

    /**
     * Original languages, not spoken ones: this is the language a title was made in, which is
     * what someone means by asking for Korean film. A shortlist, since TMDB knows every code
     * ISO has and almost all of them return nothing.
     */
    private static final List<FilterOption> LANGUAGES = List.of(
            new FilterOption("en", "English"),
            new FilterOption("ja", "Japanese"),
            new FilterOption("ko", "Korean"),
            new FilterOption("zh", "Chinese"),
            new FilterOption("hi", "Hindi"),
            new FilterOption("fr", "French"),
            new FilterOption("es", "Spanish"),
            new FilterOption("de", "German"),
            new FilterOption("it", "Italian"),
            new FilterOption("pt", "Portuguese"),
            new FilterOption("ru", "Russian"),
            new FilterOption("sv", "Swedish"),
            new FilterOption("da", "Danish"),
            new FilterOption("nl", "Dutch"),
            new FilterOption("tr", "Turkish"));

    private TmdbFilters() {}

    static List<FilterField> fields(TmdbKind kind, List<FilterOption> genres, LocalDate today) {
        List<FilterField> fields = new ArrayList<>();

        fields.add(FilterField.text("q", "Search"));
        if (!genres.isEmpty()) {
            fields.add(FilterField.multi("genres", "Genres", genres));
        }
        fields.add(FilterField.select("year", kind == TmdbKind.MOVIE ? "Release Year" : "First Aired", years(today)));
        fields.add(FilterField.select("rating", "Rating", RATINGS));
        fields.add(FilterField.select("language", "Language", LANGUAGES));

        return List.copyOf(fields);
    }

    /**
     * The chosen values as a TMDB discover query string, or empty where nothing was chosen.
     *
     * <p>Genres are comma-joined, which is how TMDB says "and" — a pipe would say "or" and
     * widen the answer where the reader asked to narrow it.
     */
    static String discoverQuery(TmdbKind kind, DiscoverFilters filters) {
        List<String> params = new ArrayList<>();

        List<String> genres = ids(filters.all("genres"));
        if (!genres.isEmpty()) {
            params.add("with_genres=" + String.join(",", genres));
        }

        Integer year = filters.number("year");
        if (year != null) {
            params.add((kind == TmdbKind.MOVIE ? "primary_release_year=" : "first_air_date_year=") + year);
        }

        Integer rating = filters.number("rating");
        if (rating != null) {
            params.add("vote_average.gte=" + rating);
        }

        String language = language(filters);
        if (language != null) {
            params.add("with_original_language=" + language);
        }

        return String.join("&", params);
    }

    /**
     * Whether one search row satisfies the filters beside the term.
     *
     * <p>TMDB's search endpoint takes none of them — it answers a name and nothing else — but
     * every row it returns carries the fields these filters read, so they are applied to the
     * answer rather than being silently dropped whenever someone types a word.
     */
    static boolean matches(TmdbKind kind, Map<String, Object> row, DiscoverFilters filters) {
        List<String> wanted = ids(filters.all("genres"));
        if (!wanted.isEmpty() && !genreIdsOf(row).containsAll(wanted)) {
            return false;
        }

        Integer year = filters.number("year");
        if (year != null && !String.valueOf(year).equals(yearOf(kind, row))) {
            return false;
        }

        Integer rating = filters.number("rating");
        if (rating != null && !(row.get("vote_average") instanceof Number score && score.doubleValue() >= rating)) {
            return false;
        }

        String language = language(filters);
        return language == null || language.equals(row.get("original_language"));
    }

    private static String language(DiscoverFilters filters) {
        String chosen = filters.one("language");
        return LANGUAGES.stream()
                .map(FilterOption::value)
                .filter(known -> known.equals(chosen))
                .findFirst()
                .orElse(null);
    }

    private static List<String> genreIdsOf(Map<String, Object> row) {
        return row.get("genre_ids") instanceof List<?> ids
                ? ids.stream().map(String::valueOf).toList()
                : List.of();
    }

    private static String yearOf(TmdbKind kind, Map<String, Object> row) {
        Object date = row.get(kind == TmdbKind.MOVIE ? "release_date" : "first_air_date");
        return date instanceof String text && text.length() >= 4 ? text.substring(0, 4) : null;
    }

    /** Guards the query string: these are ids, and only ids may be written into it. */
    private static List<String> ids(List<String> values) {
        return values.stream()
                .filter(value -> !value.isBlank() && value.chars().allMatch(Character::isDigit))
                .toList();
    }

    private static List<FilterOption> years(LocalDate today) {
        return IntStream.rangeClosed(EARLIEST_YEAR, today.getYear() + 1)
                .map(year -> EARLIEST_YEAR + today.getYear() + 1 - year)
                .mapToObj(year -> new FilterOption(String.valueOf(year), String.valueOf(year)))
                .toList();
    }
}
