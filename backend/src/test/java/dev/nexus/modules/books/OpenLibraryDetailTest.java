package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What a book's own page is given, and what is left behind on the way in. */
class OpenLibraryDetailTest {

    private static final String WORK_ID = "OL27448W";

    private OpenLibraryClient client;
    private OpenLibraryMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(OpenLibraryClient.class);
        adapter = new OpenLibraryMetadataAdapter(
                client, new OpenLibraryProperties(
                        "https://openlibrary.test", "https://covers.test", "M", "nexus-test", 20));
    }

    private Map<String, Object> detailOf(Map<String, Object> work) {
        when(client.fetchWork(WORK_ID)).thenReturn(Optional.of(work));
        return adapter.fetchDetail(WORK_ID).orElseThrow();
    }

    /** Where a book is set and who is in it are subjects too, as far as the page is concerned. */
    @SuppressWarnings("unchecked")
    @Test
    void everyKindOfSubjectEndsInOneList() {
        Map<String, Object> detail = detailOf(Map.of(
                "subjects", List.of("Fantasy", "Adventure"),
                "subject_people", List.of("Frodo Baggins"),
                "subject_places", List.of("Middle Earth"),
                "subject_times", List.of("Third Age")));

        assertThat((List<String>) detail.get("subjects"))
                .containsExactly("Fantasy", "Adventure", "Frodo Baggins", "Middle Earth", "Third Age");
    }

    @SuppressWarnings("unchecked")
    @Test
    void aSubjectListedTwiceIsShownOnce() {
        Map<String, Object> detail = detailOf(
                Map.of("subjects", List.of("Fantasy"), "subject_places", List.of("Fantasy", "Middle Earth")));

        assertThat((List<String>) detail.get("subjects")).containsExactly("Fantasy", "Middle Earth");
    }

    /** A classic carries hundreds of them, folded together from every edition's cataloguing. */
    @SuppressWarnings("unchecked")
    @Test
    void aLongSubjectListIsCutToWhatThePageShows() {
        List<String> many =
                IntStream.range(0, 60).mapToObj(index -> "Subject " + index).toList();

        Map<String, Object> detail = detailOf(Map.of("subjects", many));

        assertThat((List<String>) detail.get("subjects")).hasSize(20);
    }

    @SuppressWarnings("unchecked")
    @Test
    void aLinkKeepsTheNameItWasGiven() {
        Map<String, Object> detail = detailOf(
                Map.of("links", List.of(Map.of("title", "Wikipedia", "url", "https://en.wikipedia.org/x"))));

        assertThat((List<Map<String, Object>>) detail.get("links"))
                .containsExactly(Map.of("site", "Wikipedia", "url", "https://en.wikipedia.org/x"));
    }

    @Test
    void anExcerptIsReadFromEitherShapeItIsWrittenIn() {
        assertThat(detailOf(Map.of("excerpts", List.of(Map.of("excerpt", "It was a bright cold day")))))
                .containsEntry("excerpt", "It was a bright cold day");

        assertThat(detailOf(Map.of("excerpts", List.of(Map.of("value", "In a hole in the ground")))))
                .containsEntry("excerpt", "In a hole in the ground");
    }

    // --- the author, the ratings and the readers -----------------------------

    /** The work record names an author only by key, so the card costs a request of its own. */
    @SuppressWarnings("unchecked")
    @Test
    void anAuthorIsFetchedFromTheKeyTheWorkNamesThemBy() {
        when(client.fetchAuthor("OL26320A"))
                .thenReturn(Optional.of(Map.of(
                        "name", "J. R. R. Tolkien",
                        "bio", "English writer and philologist.",
                        "birth_date", "1892",
                        "death_date", "1973")));

        Map<String, Object> detail =
                detailOf(Map.of("authors", List.of(Map.of("author", Map.of("key", "/authors/OL26320A")))));

        Map<String, Object> author = ((List<Map<String, Object>>) detail.get("authors")).getFirst();

        assertThat(author).containsEntry("name", "J. R. R. Tolkien");
        assertThat(author).containsEntry("bio", "English writer and philologist.");
        assertThat(author).containsEntry("lived", "1892 – 1973");
        assertThat(author).containsEntry("image", "https://covers.test/a/olid/OL26320A-M.jpg");
    }

    /** A bio arrives bare or wrapped in a typed record, the way a description does. */
    @SuppressWarnings("unchecked")
    @Test
    void anAuthorsBioIsReadFromEitherShapeItIsWrittenIn() {
        when(client.fetchAuthor("OL26320A"))
                .thenReturn(Optional.of(Map.of("name", "Tolkien", "bio", Map.of("value", "Wrapped prose."))));

        Map<String, Object> detail =
                detailOf(Map.of("authors", List.of(Map.of("author", Map.of("key", "/authors/OL26320A")))));

        assertThat(((List<Map<String, Object>>) detail.get("authors")).getFirst())
                .containsEntry("bio", "Wrapped prose.");
    }

    @SuppressWarnings("unchecked")
    @Test
    void anAuthorStillAliveIsGivenOnlyTheirBirth() {
        when(client.fetchAuthor("OL1A")).thenReturn(Optional.of(Map.of("name", "Someone", "birth_date", "1948")));

        Map<String, Object> detail =
                detailOf(Map.of("authors", List.of(Map.of("author", Map.of("key", "/authors/OL1A")))));

        assertThat(((List<Map<String, Object>>) detail.get("authors")).getFirst())
                .containsEntry("lived", "Born 1948");
    }

    /** A book with forty contributors would otherwise cost forty requests. */
    @SuppressWarnings("unchecked")
    @Test
    void onlyTheAuthorsOnTheSpineAreFetched() {
        when(client.fetchAuthor(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(Map.of("name", "Someone")));

        List<Map<String, Object>> credited = IntStream.range(0, 10)
                .mapToObj(index -> Map.<String, Object>of("author", Map.of("key", "/authors/OL" + index + "A")))
                .toList();

        Map<String, Object> detail = detailOf(Map.of("authors", credited));

        assertThat((List<Map<String, Object>>) detail.get("authors")).hasSize(3);
        verify(client, times(3)).fetchAuthor(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * Written in the shape the page's other sources write their spreads, so the same chart
     * draws it without learning where it came from.
     */
    @SuppressWarnings("unchecked")
    @Test
    void theRatingSpreadIsWrittenAsEveryOtherSourceWritesOne() {
        when(client.fetchRatings(WORK_ID))
                .thenReturn(Optional.of(Map.of(
                        "summary", Map.of("average", 4.3, "count", 210),
                        "counts", Map.of("1", 2, "2", 0, "3", 12, "4", 60, "5", 136))));

        Map<String, Object> detail = detailOf(Map.of());
        Map<String, Object> stats = (Map<String, Object>) detail.get("stats");

        assertThat(detail).containsEntry("ratingAverage", 4.3).containsEntry("ratingCount", 210);
        // A star nobody gave is not a bar.
        assertThat((List<Map<String, Object>>) stats.get("scoreDistribution"))
                .containsExactly(
                        Map.of("score", 1, "amount", 2),
                        Map.of("score", 3, "amount", 12),
                        Map.of("score", 4, "amount", 60),
                        Map.of("score", 5, "amount", 136));
    }

    /** Open Library keeps the same three shelves this app does. */
    @SuppressWarnings("unchecked")
    @Test
    void readerCountsAreWrittenUnderTheStatusesCoreSpeaks() {
        when(client.fetchReadingCounts(WORK_ID))
                .thenReturn(Optional.of(Map.of(
                        "counts", Map.of("want_to_read", 4200, "currently_reading", 310, "already_read", 8800))));

        Map<String, Object> stats = (Map<String, Object>) detailOf(Map.of()).get("stats");

        assertThat((List<Map<String, Object>>) stats.get("statusDistribution"))
                .containsExactly(
                        Map.of("status", "PLANNING", "amount", 4200),
                        Map.of("status", "IN_PROGRESS", "amount", 310),
                        Map.of("status", "COMPLETED", "amount", 8800));
    }

    @Test
    void aWorkWithNothingExtraHasNoDetailWorthKeeping() {
        assertThat(detailOf(new LinkedHashMap<>())).isEmpty();
    }

    @Test
    void aWorkOpenLibraryDoesNotKnowIsNotDetailAtAll() {
        when(client.fetchWork(WORK_ID)).thenReturn(Optional.empty());

        assertThat(adapter.fetchDetail(WORK_ID)).isEmpty();
    }
}
