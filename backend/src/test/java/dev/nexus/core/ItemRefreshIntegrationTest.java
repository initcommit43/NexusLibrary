package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Refresh-on-read end to end: a stale copy is served immediately and re-fetched behind the
 * response.
 *
 * <p>The backoff window is switched off here because item ids restart with the database, so
 * one test's claim would otherwise suppress the next test's refresh. The window itself is
 * covered by {@link ItemRefreshServiceTest}.
 */
@TestPropertySource(properties = "nexus.refresh.retry-after=1ms")
class ItemRefreshIntegrationTest extends PostgresIntegrationTest {

    private static final String CACHED_TITLE = "The Legend of Zelda: Breath of the Wild";
    private static final String REFRESHED_TITLE = "The Legend of Zelda: Breath of the Wild (Definitive)";

    /** Long enough that a read waiting on the fetch could not possibly look fast. */
    private static final Duration HELD_FETCH = Duration.ofSeconds(20);

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");

        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
        http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
    }

    @Test
    void aStaleItemIsServedFromTheCacheAndRefreshedBehindTheRead() throws Exception {
        markStale(ItemState.ONGOING);

        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        when(igdbClient.findGamesByIds(any())).thenAnswer(invocation -> {
            fetchStarted.countDown();
            releaseFetch.await(HELD_FETCH.toSeconds(), TimeUnit.SECONDS);
            return List.of(GamesTestData.game(7346, REFRESHED_TITLE));
        });

        Instant readStarted = Instant.now();
        List<Map<String, Object>> dashboard =
                http.get("/entries", "Authorization", "Bearer " + token).list();
        Duration readTook = Duration.between(readStarted, Instant.now());

        assertThat(fetchStarted.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(readTook).isLessThan(HELD_FETCH.dividedBy(2));
        assertThat(dashboard).singleElement().extracting(entry -> entry.get("title")).isEqualTo(CACHED_TITLE);

        releaseFetch.countDown();
        awaitTitle(REFRESHED_TITLE);

        // The refresh found the game long since out, so this was the last one it will get.
        assertThat(cached().getItemState()).isEqualTo(ItemState.RELEASED);
        assertThat(cached().lastRefreshedAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void aReleasedItemIsNeverRefreshedHoweverOldTheCopyIs() {
        markStale(ItemState.RELEASED);

        http.get("/entries", "Authorization", "Bearer " + token);

        verify(igdbClient, after(500).never()).findGamesByIds(any());
    }

    /**
     * A game's achievement catalogue is written into the same metadata column by the Steam
     * module, and IGDB knows nothing about it. Hundreds of Steam calls must not be undone by
     * one refresh.
     */
    @Test
    void aRefreshKeepsMetadataThatAnotherModuleWrote() throws Exception {
        TrackableItem item = cached();
        item.getMetadata().put("achievements", List.of(Map.of("id", "ACH_ONE")));
        items.save(item);
        markStale(ItemState.ONGOING);

        when(igdbClient.findGamesByIds(any())).thenReturn(List.of(GamesTestData.game(7346, REFRESHED_TITLE)));

        http.get("/entries", "Authorization", "Bearer " + token);
        awaitTitle(REFRESHED_TITLE);

        assertThat(cached().getMetadata()).containsKey("achievements").containsKey("platforms");
    }

    private void markStale(ItemState state) {
        jdbc.update(
                "UPDATE trackable_item SET item_state = ?, refreshed_at = now() - INTERVAL '3 days'", state.name());
    }

    private TrackableItem cached() {
        return items.findAll().getFirst();
    }

    private void awaitTitle(String expected) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            if (expected.equals(cached().getTitle())) {
                return;
            }
            Thread.sleep(50);
        }
        fail("The cached copy was never refreshed to \"%s\"", expected);
    }
}
