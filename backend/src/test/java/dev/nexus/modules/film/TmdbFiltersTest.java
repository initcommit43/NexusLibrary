package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The films and shows filter bar, and the TMDB query a set of values turns into. */
class TmdbFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

    private static final List<FilterField.FilterOption> GENRES =
            List.of(new FilterField.FilterOption("878", "Science Fiction"));

    private static DiscoverFilters of(Map<String, List<String>> values) {
        return new DiscoverFilters(values);
    }

    private static FilterField byId(List<FilterField> fields, String id) {
        return fields.stream().filter(field -> field.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theBarIsSearchGenresYearRatingAndLanguage() {
        List<FilterField> fields = TmdbFilters.fields(TmdbKind.MOVIE, GENRES, TODAY);

        assertThat(fields.stream().map(FilterField::id))
                .containsExactly("q", "genres", "year", "rating", "language");
    }

    /** A film has a release year and a show has a first aired one; they are not the same word. */
    @Test
    void theYearIsNamedForTheKindItDates() {
        assertThat(byId(TmdbFilters.fields(TmdbKind.MOVIE, GENRES, TODAY), "year").label())
                .isEqualTo("Release Year");
        assertThat(byId(TmdbFilters.fields(TmdbKind.SHOW, GENRES, TODAY), "year").label())
                .isEqualTo("First Aired");
    }

    @Test
    void anEmptyGenreListDropsItsControl() {
        assertThat(TmdbFilters.fields(TmdbKind.MOVIE, List.of(), TODAY).stream().map(FilterField::id))
                .containsExactly("q", "year", "rating", "language");
    }

    /** TMDB reads a comma as "and" and a pipe as "or"; narrowing means the comma. */
    @Test
    void severalGenresAreJoinedByComma() {
        assertThat(TmdbFilters.discoverQuery(TmdbKind.MOVIE, of(Map.of("genres", List.of("878", "12")))))
                .isEqualTo("with_genres=878,12");
    }

    @Test
    void eachKindNamesItsOwnYearParameter() {
        assertThat(TmdbFilters.discoverQuery(TmdbKind.MOVIE, of(Map.of("year", List.of("2019")))))
                .isEqualTo("primary_release_year=2019");
        assertThat(TmdbFilters.discoverQuery(TmdbKind.SHOW, of(Map.of("year", List.of("2019")))))
                .isEqualTo("first_air_date_year=2019");
    }

    @Test
    void ratingIsAFloorAndLanguageIsTheOriginalOne() {
        assertThat(TmdbFilters.discoverQuery(
                        TmdbKind.MOVIE, of(Map.of("rating", List.of("7"), "language", List.of("ko")))))
                .contains("vote_average.gte=7")
                .contains("with_original_language=ko");
    }

    /** Values are written into a query string, so nothing that was not offered may be. */
    @Test
    void valuesFromOutsideTheOfferedListsAreLeftOut() {
        assertThat(TmdbFilters.discoverQuery(TmdbKind.MOVIE, of(Map.of("genres", List.of("878; drop")))))
                .isEmpty();
        assertThat(TmdbFilters.discoverQuery(TmdbKind.MOVIE, of(Map.of("language", List.of("klingon")))))
                .isEmpty();
    }

    @Test
    void nothingChosenNarrowsNothing() {
        assertThat(TmdbFilters.discoverQuery(TmdbKind.MOVIE, DiscoverFilters.none()))
                .isEmpty();
    }

    /**
     * Search takes no filters of its own, so they are applied to its answer instead — without
     * this a typed word would silently drop every other control on the bar.
     */
    @Test
    void searchRowsAreFilteredOnWhatTheyCarry() {
        Map<String, Object> row = Map.of(
                "genre_ids", List.of(878, 12),
                "release_date", "2019-04-24",
                "vote_average", 8.2,
                "original_language", "en");

        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("genres", List.of("878")))))
                .isTrue();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("genres", List.of("878", "27")))))
                .isFalse();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("year", List.of("2019")))))
                .isTrue();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("year", List.of("2020")))))
                .isFalse();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("rating", List.of("8")))))
                .isTrue();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("rating", List.of("9")))))
                .isFalse();
        assertThat(TmdbFilters.matches(TmdbKind.MOVIE, row, of(Map.of("language", List.of("ko")))))
                .isFalse();
    }

    /** A show dates from a different field, so a year must not read a film's one. */
    @Test
    void aShowRowIsDatedByItsFirstAirDate() {
        Map<String, Object> show = Map.of("first_air_date", "2023-05-05");

        assertThat(TmdbFilters.matches(TmdbKind.SHOW, show, of(Map.of("year", List.of("2023")))))
                .isTrue();
        assertThat(TmdbFilters.matches(TmdbKind.SHOW, show, of(Map.of("year", List.of("2019")))))
                .isFalse();
    }
}
