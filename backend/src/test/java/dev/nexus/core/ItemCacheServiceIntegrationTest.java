package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.cache.ItemNotFoundException;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ItemCacheServiceIntegrationTest extends PostgresIntegrationTest {

    @MockitoBean
    IgdbClient igdbClient;

    @Autowired
    ItemCacheService cache;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    UserEntryRepository entries;

    @BeforeEach
    void setUp() {
        entries.deleteAll();
        items.deleteAll();
        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
    }

    @Test
    void firstSightingFetchesFromTheExternalSourceAndStoresIt() {
        TrackableItem item = cache.findOrCache(Source.IGDB, GamesTestData.BOTW_ID);

        assertThat(item.getId()).isNotNull();
        assertThat(item.getTitle()).isEqualTo("The Legend of Zelda: Breath of the Wild");
        assertThat(item.getItemState()).isEqualTo(ItemState.RELEASED);
        assertThat(item.getMetadata()).containsKey("platforms");
        verify(igdbClient, times(1)).findGameById(GamesTestData.BOTW_ID);
    }

    @Test
    void aSecondLookupIsServedFromTheDatabaseWithNoExternalCall() {
        Long firstId = cache.findOrCache(Source.IGDB, GamesTestData.BOTW_ID).getId();
        Long secondId = cache.findOrCache(Source.IGDB, GamesTestData.BOTW_ID).getId();

        assertThat(secondId).isEqualTo(firstId);
        verify(igdbClient, times(1)).findGameById(GamesTestData.BOTW_ID);
        assertThat(items.count()).isEqualTo(1);
    }

    /**
     * Two requests can miss the cache at the same moment and both try to insert. The unique
     * constraint decides the winner and the loser re-reads, so callers never see a failure.
     */
    @Test
    void concurrentFirstSightingsResolveToASingleCachedRow() throws Exception {
        int threads = 8;
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try {
            List<Future<Long>> results = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    startTogether.await();
                    return cache.findOrCache(Source.IGDB, GamesTestData.BOTW_ID).getId();
                }));
            }

            startTogether.countDown();

            List<Long> ids = new java.util.ArrayList<>();
            for (Future<Long> result : results) {
                ids.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(ids).doesNotContainNull().containsOnly(ids.getFirst());
            assertThat(items.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void anItemTheSourceDoesNotHaveIsReportedAsNotFoundAndCachesNothing() {
        when(igdbClient.findGameById(anyString())).thenReturn(List.of());

        Assertions.assertThatExceptionOfType(ItemNotFoundException.class)
                .isThrownBy(() -> cache.findOrCache(Source.IGDB, "999999"));
        assertThat(items.count()).isZero();
    }

    @Test
    void aSourceWithNoRegisteredAdapterIsReportedAsNotFound() {
        Assertions.assertThatExceptionOfType(ItemNotFoundException.class)
                .isThrownBy(() -> cache.findOrCache(Source.TMDB, "550"));
    }
}
