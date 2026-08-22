package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
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

class IgdbMetadataAdapterTest {

    // 2017-03-03, Breath of the Wild's release, as IGDB sends it.
    private static final long RELEASE_EPOCH = 1488499200L;

    private IgdbClient client;
    private IgdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(IgdbClient.class);
        adapter = new IgdbMetadataAdapter(client);
    }

    @Test
    void declaresItsMediaTypeAndSource() {
        assertThat(adapter.mediaType()).isEqualTo(MediaType.GAME);
        assertThat(adapter.source()).isEqualTo(Source.IGDB);
    }

    @Test
    void mapsSearchResults() {
        when(client.searchGames(anyString(), anyInt())).thenReturn(List.of(game()));

        var results = adapter.search("zelda", 10);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().externalId()).isEqualTo("7346");
        assertThat(results.getFirst().title()).isEqualTo("The Legend of Zelda: Breath of the Wild");
        assertThat(results.getFirst().releaseDate()).isEqualTo(LocalDate.of(2017, 3, 3));
    }

    @Test
    void rewritesThumbnailCoversToFullSizeAndAbsoluteUrls() {
        when(client.findGameById(anyString())).thenReturn(List.of(game()));

        TrackableItemData data = adapter.fetchById("7346").orElseThrow();

        assertThat(data.coverUrl()).isEqualTo("https://images.igdb.com/igdb/image/upload/t_cover_big/co3p2d.jpg");
    }

    @Test
    void toleratesAGameWithNoCover() {
        Map<String, Object> game = game();
        game.remove("cover");
        when(client.findGameById(anyString())).thenReturn(List.of(game));

        assertThat(adapter.fetchById("7346").orElseThrow().coverUrl()).isNull();
    }

    @Test
    void convertsTheUnixReleaseTimestamp() {
        when(client.findGameById(anyString())).thenReturn(List.of(game()));

        assertThat(adapter.fetchById("7346").orElseThrow().releaseDate()).isEqualTo(LocalDate.of(2017, 3, 3));
    }

    @Test
    void collectsPlatformsGenresAndSummaryIntoMetadata() {
        when(client.findGameById(anyString())).thenReturn(List.of(game()));

        Map<String, Object> metadata = adapter.fetchById("7346").orElseThrow().metadata();

        assertThat(metadata).containsEntry("platforms", List.of("Nintendo Switch", "Wii U"));
        assertThat(metadata).containsEntry("genres", List.of("Adventure"));
        assertThat(metadata).containsKey("summary");
    }

    @Test
    void keepsIgdbRatingsOnTheInternalZeroToHundredScale() {
        when(client.findGameById(anyString())).thenReturn(List.of(game()));

        assertThat(adapter.fetchById("7346").orElseThrow().metadata()).containsEntry("externalRating", 97L);
    }

    @Test
    void treatsAPastReleaseAsReleasedAndAFutureOneAsUpcoming() {
        when(client.findGameById(anyString())).thenReturn(List.of(game()));
        assertThat(adapter.fetchById("7346").orElseThrow().itemState()).isEqualTo(ItemState.RELEASED);

        Map<String, Object> unreleased = game();
        unreleased.put("first_release_date", LocalDate.now().plusYears(1).toEpochDay() * 86_400L);
        when(client.findGameById(anyString())).thenReturn(List.of(unreleased));
        assertThat(adapter.fetchById("7346").orElseThrow().itemState()).isEqualTo(ItemState.UPCOMING);
    }

    @Test
    void treatsEarlyAccessAsOngoing() {
        Map<String, Object> earlyAccess = game();
        earlyAccess.put("status", 4);
        when(client.findGameById(anyString())).thenReturn(List.of(earlyAccess));

        assertThat(adapter.fetchById("7346").orElseThrow().itemState()).isEqualTo(ItemState.ONGOING);
    }

    @Test
    void treatsAGameWithNoReleaseDateAsUpcoming() {
        Map<String, Object> undated = game();
        undated.remove("first_release_date");
        when(client.findGameById(anyString())).thenReturn(List.of(undated));

        assertThat(adapter.fetchById("7346").orElseThrow().itemState()).isEqualTo(ItemState.UPCOMING);
    }

    /**
     * Guards the import path: falling back to the inherited one-at-a-time default would
     * turn a 500-game library into 500 sequential calls at four per second.
     */
    @Test
    void fetchingManyIdsUsesTheBulkEndpointRatherThanOneCallEach() {
        when(client.findGamesByIds(anyCollection())).thenReturn(List.of(game(), game(7347, "Hades")));

        var items = adapter.fetchByIds(List.of("7346", "7347"));

        assertThat(items).hasSize(2);
        verify(client, times(1)).findGamesByIds(anyCollection());
        verify(client, never()).findGameById(anyString());
    }

    @Test
    void splitsAnOversizedBatchIntoRequestsIgdbWillAccept() {
        List<String> ids = java.util.stream.IntStream.rangeClosed(1, 1200)
                .mapToObj(String::valueOf)
                .toList();
        when(client.findGamesByIds(anyCollection())).thenReturn(List.of());

        adapter.fetchByIds(ids);

        // 1200 ids over a 500-row response cap.
        verify(client, times(3)).findGamesByIds(anyCollection());
    }

    @Test
    void returnsEmptyWhenIgdbKnowsNoSuchGame() {
        when(client.findGameById(anyString())).thenReturn(List.of());

        assertThat(adapter.fetchById("999999")).isEqualTo(Optional.empty());
    }

    private Map<String, Object> game(int id, String name) {
        Map<String, Object> game = game();
        game.put("id", id);
        game.put("name", name);
        return game;
    }

    private Map<String, Object> game() {
        return new java.util.HashMap<>(Map.of(
                "id", 7346,
                "name", "The Legend of Zelda: Breath of the Wild",
                "summary", "Step into a world of discovery.",
                "first_release_date", RELEASE_EPOCH,
                "cover", Map.of("url", "//images.igdb.com/igdb/image/upload/t_thumb/co3p2d.jpg"),
                "platforms", List.of(Map.of("name", "Nintendo Switch"), Map.of("name", "Wii U")),
                "genres", List.of(Map.of("name", "Adventure")),
                "total_rating", 96.7));
    }
}
