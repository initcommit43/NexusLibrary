package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Turns Open Library's records into the shape core persists. */
class OpenLibraryMetadataAdapterTest {

    private OpenLibraryClient client;
    private OpenLibraryMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(OpenLibraryClient.class);
        adapter = new OpenLibraryMetadataAdapter(
                client, new OpenLibraryProperties("https://ol.test", "https://covers.test", "L", "Test/1.0", 100));
    }

    private Map<String, Object> dune() {
        return Map.of(
                "key", "/works/OL893414W",
                "title", "Dune",
                "author_name", List.of("Frank Herbert"),
                "first_publish_year", 1965,
                "cover_i", 11481354,
                "number_of_pages_median", 607,
                "ratings_average", 4.25,
                "subject", List.of("Science fiction", "Desert"));
    }

    /** The stored id is the bare work id; the {@code /works/} prefix is the same for all of them. */
    @Test
    void storesTheWorkIdWithoutItsPath() {
        TrackableItemData data = adapter.toItemData(dune(), null);

        assertThat(data.externalId()).isEqualTo("OL893414W");
        assertThat(data.source()).isEqualTo(Source.OPEN_LIBRARY);
        assertThat(data.mediaType()).isEqualTo(MediaType.BOOK);
    }

    @Test
    void buildsACoverUrlFromTheCoverId() {
        assertThat(adapter.toItemData(dune(), null).coverUrl())
                .isEqualTo("https://covers.test/b/id/11481354-L.jpg");
    }

    /** Open Library serves a placeholder image for an unknown id, so no cover must stay null. */
    @Test
    void leavesTheCoverNullWhenThereIsNone() {
        assertThat(adapter.toItemData(Map.of("key", "/works/OL1W", "title", "A Book"), null)
                        .coverUrl())
                .isNull();
    }

    /** Open Library records a year and no finer, so a book dates to that January. */
    @Test
    void datesABookToTheJanuaryOfItsFirstPublicationYear() {
        assertThat(adapter.toItemData(dune(), null).releaseDate()).isEqualTo(LocalDate.of(1965, 1, 1));
    }

    @Test
    void leavesTheDateNullWhenThePublicationYearIsUnusable() {
        assertThat(adapter.toItemData(Map.of("key", "/works/OL1W", "title", "A Book"), null)
                        .releaseDate())
                .isNull();
        assertThat(adapter.toItemData(
                                Map.of("key", "/works/OL1W", "title", "Ancient", "first_publish_year", 0), null)
                        .releaseDate())
                .isNull();
    }

    /** A book gains no episodes and no chapters, so it is settled and never refreshed again. */
    @Test
    void treatsAPublishedBookAsReleasedAndAFutureOneAsUpcoming() {
        assertThat(adapter.toItemData(dune(), null).itemState()).isEqualTo(ItemState.RELEASED);

        Map<String, Object> future = Map.of(
                "key", "/works/OL1W", "title", "Not Out Yet", "first_publish_year", LocalDate.now().getYear() + 2);
        assertThat(adapter.toItemData(future, null).itemState()).isEqualTo(ItemState.UPCOMING);
    }

    @Test
    void scalesTheRatingFromFiveToAHundred() {
        assertThat(adapter.toItemData(dune(), null).metadata()).containsEntry("externalRating", 85L);
    }

    @Test
    void keepsAuthorsAndPageCountAndCapsTheSubjectList() {
        Map<String, Object> data = adapter.toItemData(dune(), "A blurb.").metadata();

        assertThat(data).containsEntry("authors", List.of("Frank Herbert"));
        assertThat(data).containsEntry("pageCount", 607);
        assertThat(data).containsEntry("summary", "A blurb.");
        assertThat((List<?>) data.get("genres")).hasSizeLessThanOrEqualTo(8);
    }

    /** Open Library writes a description as a plain string or as a typed record; both are live. */
    @Test
    void readsADescriptionInEitherOfTheShapesOpenLibraryWrites() {
        when(client.findByWorkId("OL893414W")).thenReturn(Optional.of(dune()));

        when(client.fetchWork("OL893414W")).thenReturn(Optional.of(Map.of("description", "Plain string.")));
        assertThat(adapter.fetchById("OL893414W").orElseThrow().metadata())
                .containsEntry("summary", "Plain string.");

        when(client.fetchWork("OL893414W"))
                .thenReturn(Optional.of(Map.of("description", Map.of("type", "/type/text", "value", "Typed."))));
        assertThat(adapter.fetchById("OL893414W").orElseThrow().metadata()).containsEntry("summary", "Typed.");
    }

    /**
     * The bulk path deliberately skips descriptions: fetching them would cost a request per book
     * and undo the batching, for a paragraph no list view shows.
     */
    @Test
    void fetchesManyBooksWithoutPayingForTheirDescriptions() {
        when(client.findByWorkIds(any())).thenReturn(List.of(dune()));

        List<TrackableItemData> fetched = adapter.fetchByIds(List.of("OL893414W"));

        assertThat(fetched).hasSize(1);
        assertThat(fetched.getFirst().metadata()).doesNotContainKey("summary");
        verify(client, org.mockito.Mockito.never()).fetchWork(anyString());
    }

    @Test
    void dropsASearchHitWithNoTitle() {
        when(client.search(anyString(), anyInt()))
                .thenReturn(List.of(Map.of("key", "/works/OL1W"), Map.of("key", "/works/OL2W", "title", "Real")));

        assertThat(adapter.search(MediaType.BOOK, "x", 5)).hasSize(1);
    }
}
