package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.cache.TrackableItemWriter;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import dev.nexus.modules.anime.AniListClient;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** A manga going from announced to publishing, told once to everyone keeping it. */
class ReleaseStartIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    @Autowired
    TrackableItemWriter writer;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        when(anilistClient.findMediaById(eq("500"))).thenReturn(List.of(media("500", "MANGA", "Upcoming Manga")));
        when(anilistClient.findMediaById(eq("600"))).thenReturn(List.of(media("600", "ANIME", "Upcoming Anime")));
        token = registerAndGetToken(http, "reader@example.com", "reader");
    }

    @Test
    void aMangaStartingPublicationIsToldToAPlanningReaderOnce() {
        track("500", "PLANNING");

        released("500", MediaType.MANGA);
        released("500", MediaType.MANGA);

        assertThat(notifications()).singleElement()
                .satisfies(told -> assertThat(told).containsEntry("type", "RELEASE_STARTED"));
    }

    /** An anime's premiere is its first episode airing, so its status turning says nothing. */
    @Test
    void anAnimeStartingSaysNothingOfItsOwn() {
        track("600", "PLANNING");

        released("600", MediaType.ANIME);

        assertThat(notifications()).isEmpty();
    }

    // --- helpers ----------------------------------------------------------

    private void track(String externalId, String status) {
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", externalId, "status", status),
                "Authorization",
                "Bearer " + token);
        assertThat(tracked.status()).isEqualTo(201);
    }

    /** What a refresh writes once AniList has the title as releasing. */
    private void released(String externalId, MediaType mediaType) {
        writer.refreshAll(
                Source.ANILIST,
                List.of(new TrackableItemData(
                        mediaType, Source.ANILIST, externalId, "Title", null, null, ItemState.ONGOING, false, Map.of())));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> notifications() {
        Response response = http.get("/notifications", "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return (List<Map<String, Object>>) response.body().get("items");
    }

    private static Map<String, Object> media(String id, String type, String title) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", Integer.valueOf(id));
        media.put("type", type);
        media.put("status", "NOT_YET_RELEASED");
        media.put("title", new HashMap<>(Map.of("english", title)));
        return media;
    }
}
