package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        when(client.browseGames(anyString(), anyString(), anyInt(), anyInt())).thenReturn(List.of());
    }

    private String whereFor(String shelfId) {
        ArgumentCaptor<String> where = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.GAME, shelfId, 1, 20);
        verify(client).browseGames(where.capture(), anyString(), anyInt(), anyInt());
        return where.getValue();
    }

    private String sortFor(String shelfId) {
        ArgumentCaptor<String> sort = ArgumentCaptor.forClass(String.class);
        adapter.browse(MediaType.GAME, shelfId, 1, 20);
        verify(client).browseGames(anyString(), sort.capture(), anyInt(), anyInt());
        return sort.getValue();
    }

    @Test
    void offersTheShelvesInReadingOrder() {
        assertThat(adapter.browseShelves(MediaType.GAME))
                .extracting(BrowseShelf::id)
                .containsExactly(
                        "popular",
                        "playing",
                        "anticipated",
                        "top-rated",
                        "recent",
                        "coming-soon",
                        "rpg",
                        "shooter",
                        "strategy",
                        "indie",
                        "most-played");
    }

    /**
     * The whole point of the page. Browse used to be the home page again: four shelves, all
     * four on home, so opening it showed what the reader had just scrolled past.
     */
    @Test
    void offersMoreOnBrowseThanItLeadsTheHomePageWith() {
        List<BrowseShelf> shelves = adapter.browseShelves(MediaType.GAME);

        assertThat(shelves.stream().filter(BrowseShelf::onBrowse)).hasSizeGreaterThan(
                (int) shelves.stream().filter(BrowseShelf::onHome).count());
    }

    /** Every games row is a browse row; home is the subset, not the other way round. */
    @Test
    void putsEveryShelfOnBrowse() {
        assertThat(adapter.browseShelves(MediaType.GAME)).allMatch(BrowseShelf::onBrowse);
    }

    /** The home page keeps the four it always led with, in the order it led with them. */
    @Test
    void leadsTheHomePageWithTheOriginalFour() {
        assertThat(adapter.browseShelves(MediaType.GAME))
                .filteredOn(BrowseShelf::onHome)
                .extracting(BrowseShelf::id)
                .containsExactly("popular", "top-rated", "recent", "coming-soon");
    }

    /** A home leads; it does not categorise. Genre rows belong to browse alone. */
    @Test
    void keepsGenreShelvesOffTheHomePage() {
        assertThat(adapter.browseShelves(MediaType.GAME))
                .filteredOn(shelf -> List.of("rpg", "shooter", "strategy", "indie").contains(shelf.id()))
                .hasSize(4)
                .noneMatch(BrowseShelf::onHome);
    }

    /**
     * Without the high floor a genre sorted by score fills with fan projects carrying a
     * handful of perfect ratings, and the row stops being the case for the genre.
     */
    @Test
    void putsTheHighVoteFloorUnderAGenreShelf() {
        assertThat(whereFor("rpg")).contains("total_rating_count > 200");
    }

    @Test
    void queriesAGenreShelfByItsGenreId() {
        assertThat(whereFor("shooter")).contains("genres = (5)");
    }

    @Test
    void sortsAGenreShelfByScore() {
        assertThat(sortFor("strategy")).isEqualTo("total_rating desc");
    }

    /**
     * Anticipation and attention are different tables. Read from the same one, "most
     * anticipated" would just be "popular now" with unreleased games in it.
     */
    @Test
    void readsEachRankedShelfFromItsOwnPopularityTable() {
        when(client.popularGameIds(anyInt(), anyInt(), anyInt())).thenReturn(List.of());
        ArgumentCaptor<Integer> type = ArgumentCaptor.forClass(Integer.class);

        adapter.browse(MediaType.GAME, "popular", 1, 20);
        adapter.browse(MediaType.GAME, "playing", 1, 20);
        adapter.browse(MediaType.GAME, "anticipated", 1, 20);
        adapter.browse(MediaType.GAME, "most-played", 1, 20);
        verify(client, times(4)).popularGameIds(type.capture(), anyInt(), anyInt());

        assertThat(type.getAllValues()).containsExactly(1, 3, 10, 4).doesNotHaveDuplicates();
    }

    /** Without a vote floor, one enthusiastic rating outranks everything ever made. */
    @Test
    void putsAVoteFloorUnderTopRated() {
        assertThat(whereFor("top-rated")).contains("total_rating_count > 200");
    }

    @Test
    void sortsTopRatedByScore() {
        assertThat(sortFor("top-rated")).isEqualTo("total_rating desc");
    }

    /**
     * What is popular now is what people are opening now, which IGDB keeps in a table of its
     * own — a rating count is a decade of votes and answers a different question.
     */
    @Test
    void readsPopularNowFromIgdbsOwnRanking() {
        when(client.popularGameIds(anyInt(), anyInt(), anyInt())).thenReturn(List.of("7", "3"));
        when(client.findGamesByIds(anyCollection()))
                .thenReturn(List.of(Map.of("id", 3, "name", "Second"), Map.of("id", 7, "name", "First")));

        List<ItemSearchResult> ranked = adapter.browse(MediaType.GAME, "popular", 1, 20).items();

        // IGDB answers the games in whatever order it likes; the ranking is the whole point.
        assertThat(ranked).extracting(ItemSearchResult::title).containsExactly("First", "Second");
        verify(client, never()).browseGames(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void turnsAPageOfPopularNowIntoAnOffsetOnTheRanking() {
        when(client.popularGameIds(anyInt(), anyInt(), anyInt())).thenReturn(List.of());

        adapter.browse(MediaType.GAME, "popular", 3, 40);

        verify(client).popularGameIds(anyInt(), eq(80), eq(40));
    }

    /** A ranked id IGDB no longer has a game for is dropped, not held open as a gap. */
    @Test
    void dropsARankedIdWithNoGameBehindIt() {
        when(client.popularGameIds(anyInt(), anyInt(), anyInt())).thenReturn(List.of("7", "9"));
        when(client.findGamesByIds(anyCollection())).thenReturn(List.of(Map.of("id", 7, "name", "Only one")));

        assertThat(adapter.browse(MediaType.GAME, "popular", 1, 20).items()).hasSize(1);
    }

    /** A shelf of what is out next is useless sorted the other way: it opens on 2031. */
    @Test
    void ordersComingSoonByWhatIsOutNext() {
        assertThat(sortFor("coming-soon")).isEqualTo("first_release_date asc");
    }

    /** A rating shelf is about released games; an unreleased one has no score to sort by. */
    @Test
    void keepsUnreleasedGamesOffTopRated() {
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
        assertThat(adapter.browse(MediaType.GAME, "not-a-shelf", 1, 20).items()).isEmpty();
        verify(client, never()).browseGames(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test
    void mapsGamesOntoSearchResults() {
        when(client.browseGames(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1234, "name", "Hades")));

        List<ItemSearchResult> results = adapter.browse(MediaType.GAME, "top-rated", 1, 20).items();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().title()).isEqualTo("Hades");
        assertThat(results.getFirst().externalId()).isEqualTo("1234");
    }

    /** IGDB carries stub records with no name; a nameless cover is worse than a shorter row. */
    @Test
    void dropsAGameWithNoName() {
        when(client.browseGames(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1), Map.of("id", 2, "name", "Real Game")));

        assertThat(adapter.browse(MediaType.GAME, "top-rated", 1, 20).items()).hasSize(1);
    }

    /** Page two skips a page's worth, which is the whole of how a "view all" grid advances. */
    @Test
    void turnsAPageNumberIntoAnOffset() {
        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        adapter.browse(MediaType.GAME, "top-rated", 3, 40);
        verify(client).browseGames(anyString(), anyString(), offset.capture(), anyInt());

        assertThat(offset.getValue()).isEqualTo(80);
    }

    /** A full page is the only signal IGDB gives that there is another one behind it. */
    @Test
    void reportsMoreOnlyWhenThePageCameBackFull() {
        when(client.browseGames(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(Map.of("id", 1, "name", "A"), Map.of("id", 2, "name", "B")));

        assertThat(adapter.browse(MediaType.GAME, "top-rated", 1, 2).hasMore()).isTrue();
        assertThat(adapter.browse(MediaType.GAME, "top-rated", 1, 5).hasMore()).isFalse();
    }
}
