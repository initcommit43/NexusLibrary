package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nexus.core.cache.ItemRefreshRunner;
import dev.nexus.core.cache.ItemRefreshService;
import dev.nexus.core.cache.RefreshProperties;
import dev.nexus.core.cache.StalenessPolicy;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** What a read decides to put in flight, without the cost of a running application. */
class ItemRefreshServiceTest {

    private static final Duration TTL = Duration.ofHours(24);
    private static final Duration RETRY_AFTER = Duration.ofMinutes(15);

    private final ItemRefreshRunner runner = mock(ItemRefreshRunner.class);

    @Test
    void staleItemsOfOneSourceAreDispatchedAsASingleBatch() {
        ItemRefreshService service = service(50);

        service.refreshIfStale(List.of(stale(1L), stale(2L)));

        ArgumentCaptor<List<Long>> dispatched = ArgumentCaptor.captor();
        verify(runner).refresh(eq(Source.IGDB), dispatched.capture());
        assertThat(dispatched.getValue()).containsExactly(1L, 2L);
    }

    @Test
    void freshAndReleasedItemsAreLeftAlone() {
        ItemRefreshService service = service(50);

        service.refreshIfStale(List.of(
                item(1L, ItemState.ONGOING, Instant.now().minus(Duration.ofHours(1))),
                item(2L, ItemState.RELEASED, Instant.now().minus(Duration.ofDays(400)))));

        verify(runner, never()).refresh(any(), anyList());
    }

    /**
     * Concurrent reads of a shared cache land on the same titles constantly; only the first
     * of them should turn into an external call.
     */
    @Test
    void aSecondReadInsideTheRetryWindowDoesNotDispatchAgain() {
        ItemRefreshService service = service(50);
        TrackableItem item = stale(1L);

        service.refreshIfStale(item);
        service.refreshIfStale(item);

        verify(runner, times(1)).refresh(eq(Source.IGDB), anyList());
    }

    /** A first look at a large library must not queue a refresh for every stale title. */
    @Test
    void noMoreItemsGoInFlightThanOneReadIsAllowed() {
        ItemRefreshService service = service(2);

        service.refreshIfStale(List.of(stale(1L), stale(2L), stale(3L)));

        ArgumentCaptor<List<Long>> dispatched = ArgumentCaptor.captor();
        verify(runner).refresh(eq(Source.IGDB), dispatched.capture());
        assertThat(dispatched.getValue()).containsExactly(1L, 2L);
    }

    private ItemRefreshService service(int maxItemsPerRead) {
        RefreshProperties properties = new RefreshProperties(TTL, TTL, RETRY_AFTER, maxItemsPerRead);
        return new ItemRefreshService(new StalenessPolicy(properties), runner, properties);
    }

    private TrackableItem stale(long id) {
        return item(id, ItemState.ONGOING, Instant.now().minus(Duration.ofDays(2)));
    }

    /** The id and the timestamps belong to JPA and the database, so tests plant them. */
    private TrackableItem item(long id, ItemState state, Instant refreshedAt) {
        TrackableItem item = new TrackableItem(
                MediaType.GAME, Source.IGDB, "igdb-" + id, "Title " + id, null, null, state, Map.of());
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "refreshedAt", refreshedAt);
        return item;
    }
}
