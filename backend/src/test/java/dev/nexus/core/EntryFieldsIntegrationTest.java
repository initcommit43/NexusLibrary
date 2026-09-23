package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.modules.anime.AniListClient;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** What an entry carries beyond status and progress, and how an edit changes it. */
class EntryFieldsIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    private HttpTestClient http;
    private String token;
    private long entryId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime()));

        token = registerAndGetToken(http, "reader@example.com", "reader");
        entryId = track();
    }

    @Test
    void aNewEntryHasNotBeenRepeatedAndIsNeitherPrivateNorHidden() {
        Map<String, Object> entry = get();

        assertThat(entry)
                .containsEntry("repeatCount", 0)
                .containsEntry("private", false)
                .containsEntry("hiddenFromStatusLists", false);
    }

    @Test
    void anEditSetsTheRepeatCountAndBothFlags() {
        Map<String, Object> entry = patch(Map.of("repeatCount", 2, "private", true, "hiddenFromStatusLists", true))
                .body();

        assertThat(entry)
                .containsEntry("repeatCount", 2)
                .containsEntry("private", true)
                .containsEntry("hiddenFromStatusLists", true);
        assertThat(get()).containsEntry("repeatCount", 2).containsEntry("private", true);
    }

    /** Null still means "leave it", so an edit that says nothing about them keeps them. */
    @Test
    void anEditThatLeavesThemOutKeepsThem() {
        patch(Map.of("repeatCount", 3, "private", true));

        Map<String, Object> entry = patch(Map.of("notes", "again")).body();

        assertThat(entry).containsEntry("repeatCount", 3).containsEntry("private", true);
    }

    @Test
    void aNegativeRepeatCountIsRefused() {
        Response response =
                http.patchJson("/entries/" + entryId, Map.of("repeatCount", -1), "Authorization", "Bearer " + token);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("repeatCount");
    }

    /** The new fields change nothing about whose entry can be edited. */
    @Test
    void anotherReaderCannotSetThemOnThisEntry() {
        String other = registerAndGetToken(http, "other@example.com", "other");

        Response response = http.patchJson(
                "/entries/" + entryId, Map.of("private", true, "repeatCount", 9), "Authorization", "Bearer " + other);

        assertThat(response.status()).isEqualTo(404);
        assertThat(get()).containsEntry("private", false).containsEntry("repeatCount", 0);
    }

    private Map<String, Object> get() {
        Response response = http.get("/entries/" + entryId, "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    private Response patch(Map<String, Object> body) {
        Response response = http.patchJson("/entries/" + entryId, body, "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response;
    }

    private long track() {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
        assertThat(response.status()).isEqualTo(201);
        return ((Number) response.body().get("id")).longValue();
    }

    private static Map<String, Object> anime() {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("type", "ANIME");
        media.put("status", "FINISHED");
        media.put("episodes", 12);
        media.put("title", new HashMap<>(Map.of("english", "Sekirei")));
        return media;
    }
}
