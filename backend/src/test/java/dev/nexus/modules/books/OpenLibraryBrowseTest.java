package dev.nexus.modules.books;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.domain.MediaType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The Open Library lists behind the books browse page. */
class OpenLibraryBrowseTest {

    private OpenLibraryClient client;
    private OpenLibraryMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(OpenLibraryClient.class);
        adapter = new OpenLibraryMetadataAdapter(
                client, new OpenLibraryProperties("https://ol.test", "https://covers.test", "L", "Test/1.0", 100));
        when(client.trending(anyString(), anyInt(), anyInt())).thenReturn(List.of());
        when(client.subject(anyString(), anyInt(), anyInt())).thenReturn(List.of());
    }

    /**
     * One trending row ahead of the subjects. Open Library's four trending windows return the
     * same titles in a different order, so more than one would print the same shelf twice.
     */
    @Test
    void offersOneTrendingRowAheadOfTheSubjects() {
        assertThat(adapter.browseShelves(MediaType.BOOK))
                .extracting(BrowseShelf::id)
                .containsExactly(
                        "trending", "fiction", "fantasy", "science-fiction", "mystery", "horror", "romance");
    }

    @Test
    void readsTrendingFromTheWeeklyWindow() {
        adapter.browse(MediaType.BOOK, "trending", 1, 24);

        verify(client).trending("weekly", 1, 24);
    }

    /** A subject pages by offset rather than by page number, unlike trending. */
    @Test
    void turnsAPageNumberIntoAnOffsetForASubject() {
        adapter.browse(MediaType.BOOK, "fantasy", 3, 40);

        verify(client).subject("fantasy", 80, 40);
        verify(client, never()).trending(anyString(), anyInt(), anyInt());
    }

    /** Open Library spells this subject out in full, and the shelf id must not have to. */
    @Test
    void mapsAShelfIdOntoOpenLibrarysOwnSubjectName() {
        adapter.browse(MediaType.BOOK, "mystery", 1, 24);

        verify(client).subject("detective_and_mystery_stories", 0, 24);
    }

    @Test
    void asksForNothingWhenTheShelfIsUnknown() {
        assertThat(adapter.browse(MediaType.BOOK, "not-a-shelf", 1, 24).items()).isEmpty();

        verify(client, never()).trending(anyString(), anyInt(), anyInt());
        verify(client, never()).subject(anyString(), anyInt(), anyInt());
    }

    @Test
    void mapsWorksOntoResultsWithTheirCovers() {
        when(client.trending(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of(
                        "key", "/works/OL17930368W",
                        "title", "Atomic Habits",
                        "author_name", List.of("James Clear"),
                        "cover_i", 12539702,
                        "first_publish_year", 2016)));

        var result = adapter.browse(MediaType.BOOK, "trending", 1, 24).items().getFirst();

        assertThat(result.externalId()).isEqualTo("OL17930368W");
        assertThat(result.title()).isEqualTo("Atomic Habits");
        assertThat(result.coverUrl()).isEqualTo("https://covers.test/b/id/12539702-L.jpg");
        assertThat(result.facets()).containsEntry("authors", List.of("James Clear"));
    }

    /** Neither list reports a total, so a full page is the only sign of another behind it. */
    @Test
    void reportsMoreOnlyWhenThePageCameBackFull() {
        when(client.trending(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of("key", "/works/OL1W", "title", "A"), Map.of("key", "/works/OL2W", "title", "B")));

        assertThat(adapter.browse(MediaType.BOOK, "trending", 1, 2).hasMore()).isTrue();
        assertThat(adapter.browse(MediaType.BOOK, "trending", 1, 5).hasMore()).isFalse();
    }
}
