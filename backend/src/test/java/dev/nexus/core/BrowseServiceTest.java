package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.catalog.BrowseProperties;
import dev.nexus.core.catalog.BrowseService;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The cache is the reason this class exists: a browse page is four shelves of identical
 * answers, and IGDB permits four requests a second.
 */
class BrowseServiceTest {

    private final MetadataAdapter adapter = mock(MetadataAdapter.class);

    private BrowseService serviceWith(Duration ttl) {
        when(adapter.mediaTypes()).thenReturn(Set.of(MediaType.GAME));
        when(adapter.source()).thenReturn(Source.IGDB);
        return new BrowseService(new MetadataAdapterRegistry(List.of(adapter)), new BrowseProperties(ttl));
    }

    private ItemSearchResult game(String id) {
        return new ItemSearchResult(MediaType.GAME, Source.IGDB, id, "Game " + id, null, null);
    }

    /** Every reader after the first is served from memory, which is the whole point. */
    @Test
    void fetchesAShelfOnceAndServesEveryoneElseFromMemory() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt())).thenReturn(List.of(game("1")));

        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);

        verify(adapter, times(1)).browse(MediaType.GAME, "popular", 20);
    }

    /** Two shelves are two answers; caching them under one key would serve the wrong row. */
    @Test
    void cachesEachShelfSeparately() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(MediaType.GAME, "popular", 20)).thenReturn(List.of(game("1")));
        when(adapter.browse(MediaType.GAME, "coming-soon", 20)).thenReturn(List.of(game("2"), game("3")));

        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "coming-soon")).hasSize(2);
    }

    @Test
    void fetchesAgainOnceTheEntryHasExpired() {
        BrowseService service = serviceWith(Duration.ZERO);
        when(adapter.browse(any(), anyString(), anyInt())).thenReturn(List.of(game("1")));

        service.shelf(MediaType.GAME, "popular");
        service.shelf(MediaType.GAME, "popular");

        verify(adapter, times(2)).browse(MediaType.GAME, "popular", 20);
    }

    /**
     * A browse page is discovery rather than anything a reader depends on being current, so
     * yesterday's popular games beat an error page.
     */
    @Test
    void keepsServingAStaleShelfWhenTheSourceGoesDown() {
        BrowseService service = serviceWith(Duration.ZERO);
        when(adapter.browse(any(), anyString(), anyInt()))
                .thenReturn(List.of(game("1")))
                .thenThrow(new IllegalStateException("IGDB is down"));

        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
    }

    /** With nothing cached there is nothing to fall back to, and the reader should be told. */
    @Test
    void surfacesTheOutageWhenThereIsNoStaleCopyToServe() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt())).thenThrow(new IllegalStateException("IGDB is down"));

        assertThatThrownBy(() -> service.shelf(MediaType.GAME, "popular"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A failure is not cached: the next reader should get a real attempt, not the error again. */
    @Test
    void doesNotCacheAFailure() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt()))
                .thenThrow(new IllegalStateException("IGDB is down"))
                .thenReturn(List.of(game("1")));

        assertThatThrownBy(() -> service.shelf(MediaType.GAME, "popular"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.shelf(MediaType.GAME, "popular")).hasSize(1);
    }

    @Test
    void reportsTheShelvesTheAdapterOffers() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browseShelves(MediaType.GAME)).thenReturn(List.of(new BrowseShelf("popular", "Popular now")));

        assertThat(service.shelves(MediaType.GAME))
                .containsExactly(new BrowseShelf("popular", "Popular now"));
    }

    /** A source with no notion of popularity offers none, and that is not an error. */
    @Test
    void aSourceWithoutShelvesSaysSoRatherThanFailing() {
        MetadataAdapter plain = new MetadataAdapter() {

            @Override
            public Set<MediaType> mediaTypes() {
                return Set.of(MediaType.BOOK);
            }

            @Override
            public Source source() {
                return Source.OPEN_LIBRARY;
            }

            @Override
            public List<ItemSearchResult> search(MediaType mediaType, String query, int limit) {
                return List.of();
            }

            @Override
            public Optional<TrackableItemData> fetchById(String externalId) {
                return Optional.empty();
            }
        };
        BrowseService service =
                new BrowseService(new MetadataAdapterRegistry(List.of(plain)), new BrowseProperties(Duration.ZERO));

        assertThat(service.shelves(MediaType.BOOK)).isEmpty();
        assertThat(service.shelf(MediaType.BOOK, "popular")).isEmpty();
    }
}
