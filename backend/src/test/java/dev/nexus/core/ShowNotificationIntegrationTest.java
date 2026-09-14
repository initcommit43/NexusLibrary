package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.notifications.AiredEpisodeDetector;
import dev.nexus.modules.film.TmdbClient;
import dev.nexus.modules.film.TmdbKind;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Aired-episode notifications for a show that numbers its episodes per season. */
class ShowNotificationIntegrationTest extends PostgresIntegrationTest {

    private static final String SHOW = "tv:1396";

    @LocalServerPort
    int port;

    @MockitoBean
    TmdbClient tmdbClient;

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
        when(tmdbClient.findById(eq(TmdbKind.SHOW), eq("1396")))
                .thenReturn(Optional.of(Map.of(
                        "id", 1396, "name", "Breaking Bad", "status", "Returning Series",
                        "first_air_date", "2008-01-20")));
        token = registerAndGetToken(http, "reader@example.com", "reader");
    }

    /** Episode 5 of two different seasons is two things to say, and the season rides along. */
    @Test
    void theWatchingListIsToldEveryEpisodeWithItsSeason() {
        track("IN_PROGRESS");

        aired(2, 5);
        detector.sweep();
        aired(3, 5);
        detector.sweep();

        assertThat(notifications()).hasSize(2);
        assertThat(notifications()).allSatisfy(told -> assertThat(told).containsEntry("type", "EPISODE_AIRED"));
        assertThat(notifications()).extracting(this::payloadOf)
                .anySatisfy(payload -> assertThat(payload).containsEntry("season", 2).containsEntry("episode", 5))
                .anySatisfy(payload -> assertThat(payload).containsEntry("season", 3).containsEntry("episode", 5));
    }

    /** A season starting is news on any list; a later episode of it only on Watching. */
    @Test
    void everySeasonPremiereIsToldToEveryList() {
        for (String status : List.of("PLANNING", "PAUSED", "COMPLETED", "DROPPED")) {
            resetDatabase();
            token = registerAndGetToken(http, "reader@example.com", "reader");
            track(status);

            aired(4, 5);
            detector.sweep();
            assertThat(notifications()).as(status).isEmpty();

            aired(5, 1);
            detector.sweep();
            assertThat(notifications()).as(status).singleElement()
                    .satisfies(told -> assertThat(payloadOf(told)).containsEntry("season", 5).containsEntry("episode", 1));
        }
    }

    @Test
    void anEpisodeStillToComeSaysNothing() {
        track("IN_PROGRESS");

        airedAt(Instant.now().plusSeconds(3600), 2, 5);
        detector.sweep();

        assertThat(notifications()).isEmpty();
    }

    // --- helpers ----------------------------------------------------------

    private void track(String status) {
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "TMDB", "externalId", SHOW, "status", status),
                "Authorization",
                "Bearer " + token);
        assertThat(tracked.status()).isEqualTo(201);
    }

    private void aired(int season, int episode) {
        airedAt(Instant.now().minusSeconds(60), season, episode);
    }

    private void airedAt(Instant when, int season, int episode) {
        TrackableItem item = items.findBySourceAndExternalId(Source.TMDB, SHOW).orElseThrow();
        item.refreshFrom(
                item.getTitle(),
                item.getCoverUrl(),
                item.getReleaseDate(),
                item.getItemState(),
                Map.of("nextSeasonEpisode",
                        Map.of("season", season, "episode", episode, "airingAt", when.getEpochSecond())),
                item.getRefreshedAt());
        items.saveAndFlush(item);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> notifications() {
        Response response = http.get("/notifications", "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return (List<Map<String, Object>>) response.body().get("items");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(Map<String, Object> notification) {
        return (Map<String, Object>) notification.get("payload");
    }
}
