package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.notifications.AiredEpisodeDetector;
import dev.nexus.modules.anime.AniListClient;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** What was waiting while the reader was away: an episode that aired, and reading it. */
class NotificationIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    @Autowired
    AiredEpisodeDetector detector;

    @Autowired
    TrackableItemRepository items;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime()));
        token = registerAndGetToken(http, "reader@example.com", "reader");
    }

    @Test
    void aFreshReaderHasNothingWaiting() {
        assertThat(waiting().get("items")).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .isEmpty();
        assertThat(waiting()).containsEntry("unread", 0);
    }

    /** The point of the thing: an episode landed, and it was there when the reader arrived. */
    @Test
    void anEpisodeThatHasAiredIsWaiting() {
        track();
        airedAt(Instant.now().minusSeconds(3600), 9);

        detector.sweep();

        assertThat(notifications()).singleElement().satisfies(waiting -> {
            assertThat(waiting).containsEntry("type", "EPISODE_AIRED");
            assertThat(waiting).containsEntry("title", "Sekirei");
            assertThat(waiting).containsEntry("read", false);
            assertThat(payloadOf(waiting)).containsEntry("episode", 9);
        });
    }

    /** An episode airs tonight, not now: nothing is said until it has. */
    @Test
    void anEpisodeStillToComeSaysNothing() {
        track();
        airedAt(Instant.now().plusSeconds(3600), 9);

        detector.sweep();

        assertThat(notifications()).isEmpty();
    }

    /**
     * The sweep runs every quarter of an hour and the item keeps saying the same episode until
     * the source is asked again, so the second sweep must have nothing to add.
     */
    @Test
    void theSameEpisodeIsNeverSaidTwice() {
        track();
        airedAt(Instant.now().minusSeconds(3600), 9);

        detector.sweep();
        detector.sweep();

        assertThat(notifications()).hasSize(1);
    }

    /** Paused, dropped, planned: a shelf is not a subscription list. */
    @Test
    void everyStatusIsToldAboutIt() {
        track("PAUSED");
        airedAt(Instant.now().minusSeconds(60), 3);

        detector.sweep();

        assertThat(notifications()).hasSize(1);
    }

    @Test
    void readingThemAllLeavesNothingNew() {
        track();
        airedAt(Instant.now().minusSeconds(60), 9);
        detector.sweep();

        Response read = http.post("/notifications/read", "Authorization", "Bearer " + token);

        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body()).containsEntry("unread", 0);
        assertThat(notifications()).singleElement().satisfies(seen -> assertThat(seen)
                .containsEntry("read", true));
    }

    /** Reading one leaves the others alone: it is the row that was opened, not the list. */
    @Test
    void readingOneLeavesTheRestNew() {
        track();
        airedAt(Instant.now().minusSeconds(3600), 9);
        detector.sweep();
        airedAt(Instant.now().minusSeconds(60), 10);
        detector.sweep();

        Object first = notifications().get(0).get("id");
        Response read = http.post("/notifications/" + first + "/read", "Authorization", "Bearer " + token);

        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body()).containsEntry("unread", 1);
    }

    /** Somebody else's id changes nothing, and says nothing about whose it was. */
    @Test
    void readingSomebodyElsesChangesNothing() {
        track();
        airedAt(Instant.now().minusSeconds(60), 9);
        detector.sweep();
        Object mine = notifications().get(0).get("id");

        String stranger = registerAndGetToken(http, "stranger@example.com", "stranger");
        Response theirs = http.post("/notifications/" + mine + "/read", "Authorization", "Bearer " + stranger);

        assertThat(theirs.status()).isEqualTo(200);
        assertThat(waiting()).containsEntry("unread", 1);
    }

    /** The panel shows one module at a time, and the count beside it has to mean the same. */
    @Test
    void anotherModulesTypesHoldNoneOfIt() {
        track();
        airedAt(Instant.now().minusSeconds(60), 9);
        detector.sweep();

        Response games = http.get("/notifications?mediaTypes=GAME", "Authorization", "Bearer " + token);

        assertThat(games.body()).containsEntry("unread", 0);
        assertThat(games.body().get("items")).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .isEmpty();

        Response anime = http.get("/notifications?mediaTypes=ANIME", "Authorization", "Bearer " + token);
        assertThat(anime.body()).containsEntry("unread", 1);
    }

    /** "Read all" under one module's heading means the ones under it. */
    @Test
    void readingAllOfAnotherModuleLeavesTheseNew() {
        track();
        airedAt(Instant.now().minusSeconds(60), 9);
        detector.sweep();

        http.post("/notifications/read?mediaTypes=GAME", "Authorization", "Bearer " + token);

        assertThat(waiting()).containsEntry("unread", 1);
    }

    /** A notification is one reader's; another's list must never carry it. */
    @Test
    void nothingWaitsForSomebodyElse() {
        track();
        airedAt(Instant.now().minusSeconds(60), 9);
        detector.sweep();

        String stranger = registerAndGetToken(http, "stranger@example.com", "stranger");
        Response theirs = http.get("/notifications", "Authorization", "Bearer " + stranger);

        assertThat(theirs.body().get("unread")).isEqualTo(0);
    }

    @Test
    void notificationsRequireAuthentication() {
        assertThat(http.get("/notifications").status()).isEqualTo(401);
        assertThat(http.post("/notifications/read").status()).isEqualTo(401);
    }

    // --- helpers ----------------------------------------------------------

    private void track() {
        track("IN_PROGRESS");
    }

    private void track(String status) {
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", status),
                "Authorization",
                "Bearer " + token);
        assertThat(tracked.status()).isEqualTo(201);
    }

    /** Writes the airing time onto the item, which is where a shelf reads its countdown from. */
    private void airedAt(Instant when, int episode) {
        TrackableItem item = items.findBySourceAndExternalId(Source.ANILIST, "21").orElseThrow();

        item.refreshFrom(
                item.getTitle(),
                item.getCoverUrl(),
                item.getReleaseDate(),
                item.getItemState(),
                Map.of("nextEpisode", Map.of("episode", episode, "airingAt", when.getEpochSecond())),
                item.getRefreshedAt());
        items.saveAndFlush(item);
    }

    private Map<String, Object> waiting() {
        Response response = http.get("/notifications", "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> notifications() {
        return (List<Map<String, Object>>) waiting().get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(Map<String, Object> notification) {
        return (Map<String, Object>) notification.get("payload");
    }

    private static Map<String, Object> anime() {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("type", "ANIME");
        media.put("status", "RELEASING");
        media.put("episodes", 13);
        media.put("title", new HashMap<>(Map.of("english", "Sekirei")));
        return media;
    }
}
