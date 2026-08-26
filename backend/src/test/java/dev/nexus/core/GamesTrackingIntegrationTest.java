package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Drives the whole Games slice over real HTTP against real Postgres, with only the IGDB
 * transport stubbed.
 */
class GamesTrackingIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @Autowired
    AppUserRepository users;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "player@example.com", "player");

        when(igdbClient.searchGames(anyString(), anyInt())).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(eq(GamesTestData.HADES_ID))).thenReturn(List.of(GamesTestData.hades()));
    }

    @Test
    void searchReturnsGamesAndCachesNothing() {
        Response response = get("/catalog/search?mediaType=GAME&q=zelda");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.list()).hasSize(1);
        assertThat(response.list().getFirst()).containsEntry("title", "The Legend of Zelda: Breath of the Wild");
        // Browsing must not fill the shared cache with titles nobody tracks.
        assertThat(items.count()).isZero();
    }

    @Test
    void trackingAGameCachesItAndCreatesTheEntry() {
        Response tracked = track(GamesTestData.BOTW_ID, "PLANNING");

        assertThat(tracked.status()).isEqualTo(201);
        assertThat(tracked.body()).containsEntry("title", "The Legend of Zelda: Breath of the Wild");
        assertThat(tracked.body()).containsEntry("status", "PLANNING");
        assertThat(items.count()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    void aTrackedGameShowsUpInTheTrackedList() {
        track(GamesTestData.BOTW_ID, "IN_PROGRESS");

        Response list = get("/entries");

        assertThat(list.status()).isEqualTo(200);
        assertThat(list.list()).hasSize(1);
        assertThat(list.list().getFirst()).containsEntry("status", "IN_PROGRESS");
    }

    @Test
    void trackingTheSameGameTwiceUpdatesRatherThanDuplicating() {
        track(GamesTestData.BOTW_ID, "PLANNING");
        track(GamesTestData.BOTW_ID, "COMPLETED");

        assertThat(entries.count()).isEqualTo(1);
        assertThat(get("/entries").list().getFirst()).containsEntry("status", "COMPLETED");
    }

    /** The scalability claim: API traffic scales with distinct titles, not with users. */
    @Test
    void aSecondUserTrackingTheSameGameCostsNoExtraApiCall() {
        track(GamesTestData.BOTW_ID, "COMPLETED");
        verify(igdbClient, times(1)).findGameById(GamesTestData.BOTW_ID);

        String otherToken = registerAndGetToken(http, "second@example.com", "second");
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", "PLANNING"),
                "Authorization",
                "Bearer " + otherToken);

        assertThat(tracked.status()).isEqualTo(201);
        // Still exactly one call and one cached row, now serving two users.
        verify(igdbClient, times(1)).findGameById(GamesTestData.BOTW_ID);
        assertThat(items.count()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(2);
    }

    @Test
    void updatingAnEntryChangesStatusRatingAndProgress() {
        long id = trackedEntryId();

        Response updated = http.patchJson(
                "/entries/" + id,
                Map.of("status", "COMPLETED", "rating", 92, "progressCurrent", 5400, "progressUnit", "MINUTES"),
                "Authorization",
                "Bearer " + token);

        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body()).containsEntry("status", "COMPLETED");
        assertThat(updated.body()).containsEntry("rating", 92);
        assertThat(updated.body()).containsEntry("progressCurrent", 5400);
    }

    @Test
    void deletingAnEntryRemovesItButKeepsTheSharedCachedItem() {
        long id = trackedEntryId();

        assertThat(http.delete("/entries/" + id, "Authorization", "Bearer " + token)
                        .status())
                .isEqualTo(204);
        assertThat(entries.count()).isZero();
        // The cached item stays: it is shared, not owned by whoever tracked it first.
        assertThat(items.count()).isEqualTo(1);
    }

    @Test
    void aRatingOutsideZeroToHundredIsRejected() {
        long id = trackedEntryId();

        Response response =
                http.patchJson("/entries/" + id, Map.of("rating", 250), "Authorization", "Bearer " + token);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("rating");
    }

    @Test
    void aStatusOutsideTheEnumIsRejectedAsABadRequest() {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", "NONSENSE"),
                "Authorization",
                "Bearer " + token);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("message").toString()).doesNotContain("Exception");
    }

    @Test
    void aMissingSearchParameterIsABadRequestRatherThanAServerError() {
        assertThat(get("/catalog/search?mediaType=GAME").status()).isEqualTo(400);
        assertThat(get("/catalog/search?q=zelda").status()).isEqualTo(400);
    }

    @Test
    void anEmptySearchQueryIsRejected() {
        assertThat(get("/catalog/search?mediaType=GAME&q=").status()).isEqualTo(400);
    }

    @Test
    void anUnknownRouteIsNotFoundRatherThanAServerError() {
        assertThat(get("/entries/not-a-number").status()).isEqualTo(400);
        assertThat(get("/nope").status()).isIn(401, 404);
    }

    @Test
    void trackingAGameIgdbDoesNotKnowIsNotFound() {
        when(igdbClient.findGameById(eq("999999"))).thenReturn(List.of());

        assertThat(track("999999", "PLANNING").status()).isEqualTo(404);
        assertThat(items.count()).isZero();
    }

    @Test
    void everyTrackingEndpointRequiresAuthentication() {
        assertThat(http.get("/entries").status()).isEqualTo(401);
        assertThat(http.get("/catalog/search?mediaType=GAME&q=zelda").status()).isEqualTo(401);
        assertThat(http.postJson("/entries", Map.of("source", "IGDB", "externalId", "1", "status", "PLANNING"))
                        .status())
                .isEqualTo(401);
    }

    @Test
    void searchIsNeverRunForAnUnauthenticatedCaller() {
        http.get("/catalog/search?mediaType=GAME&q=zelda");

        verify(igdbClient, never()).searchGames(anyString(), anyInt());
    }

    private long trackedEntryId() {
        return ((Number) track(GamesTestData.BOTW_ID, "PLANNING").body().get("id")).longValue();
    }

    private Response track(String externalId, String status) {
        return http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", externalId, "status", status),
                "Authorization",
                "Bearer " + token);
    }

    private Response get(String path) {
        return http.get(path, "Authorization", "Bearer " + token);
    }
}
