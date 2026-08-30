package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.BrowseResults;
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
        return new BrowseService(new MetadataAdapterRegistry(List.of(adapter)), List.of(), new BrowseProperties(ttl));
    }

    private BrowseResults results(ItemSearchResult... items) {
        return new BrowseResults(List.of(items), false);
    }

    private ItemSearchResult game(String id) {
        return new ItemSearchResult(MediaType.GAME, Source.IGDB, id, "Game " + id, null, null);
    }

    /** Every reader after the first is served from memory, which is the whole point. */
    @Test
    void fetchesAShelfOnceAndServesEveryoneElseFromMemory() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenReturn(results(game("1")));

        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);

        verify(adapter, times(1)).browse(MediaType.GAME, "popular", 1, 24);
    }

    /** Two shelves are two answers; caching them under one key would serve the wrong row. */
    @Test
    void cachesEachShelfSeparately() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(MediaType.GAME, "popular", 1, 24)).thenReturn(results(game("1")));
        when(adapter.browse(MediaType.GAME, "coming-soon", 1, 24)).thenReturn(results(game("2"), game("3")));

        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "coming-soon").items()).hasSize(2);
    }

    /**
     * An expired shelf is replaced behind the reader rather than in front of them: the copy
     * on hand is handed over at once and the source is asked again on another thread.
     */
    @Test
    void replacesAnExpiredShelfWithoutMakingTheReaderWait() {
        BrowseService service = serviceWith(Duration.ZERO);
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenReturn(results(game("1")));

        service.shelf(MediaType.GAME, "popular");
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenReturn(results(game("2")));

        // The second read is the copy already held, not the new one.
        assertThat(service.shelf(MediaType.GAME, "popular").items())
                .singleElement()
                .satisfies(item -> assertThat(item.externalId()).isEqualTo("1"));

        verify(adapter, timeout(2000).times(2)).browse(MediaType.GAME, "popular", 1, 24);

        // And by a later read it is the new one.
        assertThat(eventualFirstId(service)).isEqualTo("2");
    }

    /** The replacement lands on another thread, so a read polls for it rather than assuming. */
    private String eventualFirstId(BrowseService service) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String id = service.shelf(MediaType.GAME, "popular")
                    .items()
                    .getFirst()
                    .externalId();
            if ("2".equals(id)) {
                return id;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    /**
     * A browse page is discovery rather than anything a reader depends on being current, so
     * yesterday's popular games beat an error page.
     */
    @Test
    void keepsServingAStaleShelfWhenTheSourceGoesDown() {
        BrowseService service = serviceWith(Duration.ZERO);
        when(adapter.browse(any(), anyString(), anyInt(), anyInt()))
                .thenReturn(results(game("1")))
                .thenThrow(new IllegalStateException("IGDB is down"));

        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
    }

    /** With nothing cached there is nothing to fall back to, and the reader should be told. */
    @Test
    void surfacesTheOutageWhenThereIsNoStaleCopyToServe() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenThrow(new IllegalStateException("IGDB is down"));

        assertThatThrownBy(() -> service.shelf(MediaType.GAME, "popular"))
                .isInstanceOf(IllegalStateException.class);
    }

    /** A failure is not cached: the next reader should get a real attempt, not the error again. */
    @Test
    void doesNotCacheAFailure() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("IGDB is down"))
                .thenReturn(results(game("1")));

        assertThatThrownBy(() -> service.shelf(MediaType.GAME, "popular"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.shelf(MediaType.GAME, "popular").items()).hasSize(1);
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
                new BrowseService(new MetadataAdapterRegistry(List.of(plain)), List.of(), new BrowseProperties(Duration.ZERO));

        assertThat(service.shelves(MediaType.BOOK)).isEmpty();
        assertThat(service.shelf(MediaType.BOOK, "popular").items()).isEmpty();
    }

    /**
     * A shelf row asks for 24 and a grid page for 40. Serving one from the other's copy would
     * leave a gap: page two starts where a page of 40 ended, not where a row of 24 did.
     */
    @Test
    void keepsTheShelfRowAndTheGridPageApart() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenReturn(results(game("1")));

        service.shelf(MediaType.GAME, "popular");
        service.page(MediaType.GAME, "popular", 1);

        verify(adapter).browse(MediaType.GAME, "popular", 1, 24);
        verify(adapter).browse(MediaType.GAME, "popular", 1, 40);
    }

    /** Page one is what everyone lands on; page seven is one reader, and is not worth holding. */
    @Test
    void cachesTheFirstGridPageButNotTheOnesBehindIt() {
        BrowseService service = serviceWith(Duration.ofHours(6));
        when(adapter.browse(any(), anyString(), anyInt(), anyInt())).thenReturn(results(game("1")));

        service.page(MediaType.GAME, "popular", 1);
        service.page(MediaType.GAME, "popular", 1);
        service.page(MediaType.GAME, "popular", 4);
        service.page(MediaType.GAME, "popular", 4);

        verify(adapter, times(1)).browse(MediaType.GAME, "popular", 1, 40);
        verify(adapter, times(2)).browse(MediaType.GAME, "popular", 4, 40);
    }
}
