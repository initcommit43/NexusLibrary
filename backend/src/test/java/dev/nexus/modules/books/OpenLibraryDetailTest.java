package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    void aCoverIdBecomesSomethingThePageCanLoad() {
        Map<String, Object> detail = detailOf(Map.of("covers", List.of(8231856, 12345)));

        assertThat((List<String>) detail.get("covers"))
                .containsExactly("https://covers.test/b/id/8231856-M.jpg", "https://covers.test/b/id/12345-M.jpg");
    }

    /** Open Library records "this work has no cover" as a cover whose id is -1. */
    @Test
    void theAbsenceOfACoverIsNotACover() {
        assertThat(detailOf(Map.of("covers", List.of(-1)))).doesNotContainKey("covers");
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
