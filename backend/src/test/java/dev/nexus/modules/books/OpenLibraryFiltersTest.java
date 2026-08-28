package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The books filter bar, and the Open Library query a set of values turns into. */
class OpenLibraryFiltersTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 29);

    private static DiscoverFilters of(Map<String, List<String>> values) {
        return new DiscoverFilters(values);
    }

    @Test
    void theBarIsSearchSubjectYearAndLanguage() {
        assertThat(OpenLibraryFilters.fields(TODAY).stream().map(FilterField::id))
                .containsExactly("q", "subject", "year", "language");
    }

    /**
     * A single subject, not several: Open Library reads a repeated one as closer to "or" than
     * "and", so a multi-select would promise a narrowing it does not perform.
     */
    @Test
    void subjectTakesOneValue() {
        FilterField subject = OpenLibraryFilters.fields(TODAY).stream()
                .filter(field -> field.id().equals("subject"))
                .findFirst()
                .orElseThrow();

        assertThat(subject.kind()).isEqualTo(FilterField.Kind.SELECT);
    }

    @Test
    void chosenValuesBecomeParameters() {
        List<String> query = OpenLibraryFilters.query(of(Map.of(
                "q", List.of("dune"),
                "subject", List.of("science_fiction"),
                "language", List.of("eng"),
                "year", List.of("1965"))));

        assertThat(query)
                .containsSequence("q", "dune")
                .containsSequence("subject", "science_fiction")
                .containsSequence("language", "eng")
                .containsSequence("first_publish_year", "1965");
    }

    /**
     * Relevance is the better order for a term, and overriding it with rating answers
     * well-regarded books that have little to do with what was asked for.
     */
    @Test
    void ratingOrdersOnlyWhatWasNotTypedFor() {
        assertThat(OpenLibraryFilters.query(DiscoverFilters.none())).containsSequence("sort", "rating");
        assertThat(OpenLibraryFilters.query(of(Map.of("q", List.of("dune"))))).doesNotContain("sort");
    }

    @Test
    void valuesFromOutsideTheOfferedListsAreLeftOut() {
        assertThat(OpenLibraryFilters.query(of(Map.of("subject", List.of("subject=x&q=y")))))
                .doesNotContain("subject");
        assertThat(OpenLibraryFilters.query(of(Map.of("language", List.of("klingon")))))
                .doesNotContain("language");
        assertThat(OpenLibraryFilters.query(of(Map.of("year", List.of("1200")))))
                .doesNotContain("first_publish_year");
    }

    @Test
    void yearsRunFromThisYearBackwards() {
        List<String> years = OpenLibraryFilters.fields(TODAY).stream()
                .filter(field -> field.id().equals("year"))
                .findFirst()
                .orElseThrow()
                .options()
                .stream()
                .map(FilterField.FilterOption::value)
                .toList();

        assertThat(years).startsWith("2026", "2025");
        assertThat(years).endsWith("1900");
    }
}
