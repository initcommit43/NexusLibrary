package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.domain.MediaType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The queries behind the games browse page, and the shelves it offers. */
class IgdbBrowseTest {

    private IgdbClient client;
    private IgdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(IgdbClient.class);
        adapter = new IgdbMetadataAdapter(client);
        when(client.browseGames(anyString(), anyString(), anyInt())).thenReturn(List.of());
    }

    private String whereFor(String shelfId) {
        ArgumentCaptor<String> where = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.GAME, shelfId, 20);
        verify(client).browseGames(where.capture(), anyString(), anyInt());
        return where.getValue();
    }

    private String sortFor(String shelfId) {
        ArgumentCaptor<String> sort = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.GAME, shelfId, 20);
        verify(client).browseGames(anyString(), sort.capture(), anyInt());
        return sort.getValue();
    }

    @Test
    void offersTheFourShelvesInReadingOrder() {
        assertThat(adapter.browseShelves(MediaType.GAME))
                .extracting(BrowseShelf::id)
                .containsExactly("popular", "top-rated", "coming-soon", "recent");
    }

    /** Without a vote floor, one enthusiastic rating outranks everything ever made. */
    @Test
    void putsAVoteFloorUnderBothRatingShelves() {
        assertThat(whereFor("popular")).contains("total_rating_count > 20");
        setUp();
        assertThat(whereFor("top-rated")).contains("total_rating_count > 200");
    }

    @Test
    void sortsPopularByVoteCountAndTopRatedByScore() {
        assertThat(sortFor("popular")).isEqualTo("total_rating_count desc");
        setUp();
        assertThat(sortFor("top-rated")).isEqualTo("total_rating desc");
    }

    /** A shelf of what is out next is useless sorted the other way: it opens on 2031. */
    @Test
    void ordersComingSoonByWhatIsOutNext() {
        assertThat(sortFor("coming-soon")).isEqualTo("first_release_date asc");
    }

    /** Both rating shelves are about released games; an unreleased one has no score to sort by. */
    @Test
    void keepsUnreleasedGamesOffTheRatingShelves() {
        assertThat(whereFor("popular")).contains("first_release_date <");
        setUp();
        assertThat(whereFor("top-rated")).contains("first_release_date <");
    }

    @Test
    void boundsRecentlyReleasedAtBothEnds() {
        String where = whereFor("recent");

        assertThat(where).contains("first_release_date >").contains("first_release_date <");
    }

    /** An id no shelf claims is a bug, not user input, and must not become a query. */
    @Test
    void asksForNothingWhenTheShelfIsUnknown() {
        assertThat(adapter.browse(MediaType.GAME, "not-a-shelf", 20)).isEmpty();
        verify(client, never()).browseGames(anyString(), anyString(), anyInt());
    }

    @Test
    void mapsGamesOntoSearchResults() {
        when(client.browseGames(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1234, "name", "Hades")));

        List<ItemSearchResult> results = adapter.browse(MediaType.GAME, "popular", 20);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().title()).isEqualTo("Hades");
        assertThat(results.getFirst().externalId()).isEqualTo("1234");
    }

    /** IGDB carries stub records with no name; a nameless cover is worse than a shorter row. */
    @Test
    void dropsAGameWithNoName() {
        when(client.browseGames(anyString(), anyString(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1), Map.of("id", 2, "name", "Real Game")));

        assertThat(adapter.browse(MediaType.GAME, "popular", 20)).hasSize(1);
    }
}
