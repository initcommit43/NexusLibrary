package dev.nexus.modules.anime;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.notifications.AiredEpisodeDetector;
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

/** What AniList noticed, brought in as the history the app was not running for. */
class AniListNotificationImportTest extends PostgresIntegrationTest {

    private static final Instant LAST_NIGHT = Instant.parse("2026-08-30T21:00:00Z");

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    @Autowired
    AniListNotificationWriter writer;

    @Autowired
    AiredEpisodeDetector detector;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;
    private String token;
    private Long userId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime()));
        token = registerAndGetToken(http, "reader@example.com", "reader");
        userId = users.findByEmail("reader@example.com").orElseThrow().getId();
    }

    @Test
    void anAiredEpisodeArrivesAgainstTheTitleOnTheShelf() {
        track();

        assertThat(writer.save(userId, List.of(aired(9, LAST_NIGHT))).stored()).isEqualTo(1);

        assertThat(notifications()).singleElement().satisfies(waiting -> {
            assertThat(waiting).containsEntry("type", "EPISODE_AIRED");
            assertThat(waiting).containsEntry("title", "Sekirei");
            assertThat(payloadOf(waiting)).containsEntry("episode", 9);
        });
    }

    /**
     * The point of carrying AniList's own stamp: a stream brought in at once is years of
     * events, and a feed sorted by when the rows were written would stack them all under
     * this minute.
     */
    @Test
    void itKeepsTheMomentAniListGivesIt() {
        track();

        writer.save(userId, List.of(aired(9, LAST_NIGHT)));

        assertThat(notifications()).singleElement().satisfies(waiting ->
                assertThat(Instant.parse(String.valueOf(waiting.get("createdAt")))).isEqualTo(LAST_NIGHT));
    }

    @Test
    void aSecondRunWritesNothing() {
        track();

        writer.save(userId, List.of(aired(9, LAST_NIGHT)));
        AniListNotificationWriter.Written again = writer.save(userId, List.of(aired(9, LAST_NIGHT)));

        assertThat(again.stored()).isZero();
        assertThat(again.known()).isEqualTo(1);
        assertThat(notifications()).hasSize(1);
    }

    /**
     * The sweep here and the notification there are one event, not two — whichever noticed
     * the episode first is the one that gets written.
     */
    @Test
    void anEpisodeTheSweepAlreadyFoundIsNotSaidTwice() {
        track();
        airedAt(Instant.now().minusSeconds(3600), 9);
        detector.sweep();

        assertThat(writer.save(userId, List.of(aired(9, LAST_NIGHT))).stored()).isZero();
        assertThat(notifications()).hasSize(1);
    }

    /** The feed is drawn from the shelf: a title the reader does not keep has nothing to say. */
    @Test
    void aTitleOffTheShelfIsSkipped() {
        AniListNotificationWriter.Written written = writer.save(userId, List.of(aired(9, LAST_NIGHT)));

        assertThat(written.stored()).isZero();
        assertThat(written.unmatched()).isEqualTo(1);
    }

    // --- helpers ----------------------------------------------------------

    private void track() {
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
        assertThat(tracked.status()).isEqualTo(201);
    }

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

    /** One AIRING notification, shaped as AniList hands it over. */
    private static Map<String, Object> aired(int episode, Instant when) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 900000 + episode);
        row.put("type", "AIRING");
        row.put("episode", episode);
        row.put("createdAt", when.getEpochSecond());
        row.put("media", anime());
        return row;
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
}
