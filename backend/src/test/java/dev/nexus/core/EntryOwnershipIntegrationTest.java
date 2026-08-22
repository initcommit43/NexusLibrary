package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
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
 * Object-level authorization, which plan.md calls the number one bug in multi-user apps.
 *
 * <p>Every case here is one user reaching for another user's row. All of them must answer
 * 404 rather than 403: a 403 would confirm the row exists, which is itself a disclosure.
 */
class EntryOwnershipIntegrationTest extends PostgresIntegrationTest {

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
    private String ownerToken;
    private String intruderToken;
    private long ownersEntryId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(eq(GamesTestData.HADES_ID))).thenReturn(List.of(GamesTestData.hades()));

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        ownersEntryId = ((Number) track(ownerToken, GamesTestData.BOTW_ID).body().get("id")).longValue();
    }

    @Test
    void anotherUserCannotReadYourEntry() {
        assertThat(get("/entries/" + ownersEntryId, intruderToken).status()).isEqualTo(404);
    }

    @Test
    void anotherUserCannotUpdateYourEntry() {
        Response response = http.patchJson(
                "/entries/" + ownersEntryId,
                Map.of("status", "DROPPED", "rating", 1),
                "Authorization",
                "Bearer " + intruderToken);

        assertThat(response.status()).isEqualTo(404);
        // And the owner's data is untouched.
        assertThat(get("/entries/" + ownersEntryId, ownerToken).body()).containsEntry("status", "PLANNING");
    }

    @Test
    void anotherUserCannotDeleteYourEntry() {
        assertThat(http.delete("/entries/" + ownersEntryId, "Authorization", "Bearer " + intruderToken)
                        .status())
                .isEqualTo(404);
        assertThat(entries.count()).isEqualTo(1);
    }

    @Test
    void listingOnlyEverReturnsYourOwnEntries() {
        track(intruderToken, GamesTestData.HADES_ID);

        List<Map<String, Object>> ownersList = get("/entries", ownerToken).list();
        List<Map<String, Object>> intrudersList = get("/entries", intruderToken).list();

        assertThat(ownersList).hasSize(1);
        assertThat(ownersList.getFirst()).containsEntry("title", "The Legend of Zelda: Breath of the Wild");
        assertThat(intrudersList).hasSize(1);
        assertThat(intrudersList.getFirst()).containsEntry("title", "Hades");
    }

    @Test
    void tellingTheServerADifferentUserIdChangesNothing() {
        // The user id comes from the token, never from the payload, so a forged field is inert.
        Response response = http.postJson(
                "/entries",
                Map.of(
                        "source", "IGDB",
                        "externalId", GamesTestData.HADES_ID,
                        "status", "PLANNING",
                        "userId", 1),
                "Authorization",
                "Bearer " + intruderToken);

        assertThat(response.status()).isEqualTo(201);
        assertThat(get("/entries", ownerToken).list()).hasSize(1);
        assertThat(get("/entries", intruderToken).list()).hasSize(1);
    }

    @Test
    void twoUsersCanTrackTheSameGameWithoutSeeingEachOthersProgress() {
        track(intruderToken, GamesTestData.BOTW_ID);

        http.patchJson(
                "/entries/" + ownersEntryId,
                Map.of("status", "COMPLETED", "rating", 95),
                "Authorization",
                "Bearer " + ownerToken);

        Map<String, Object> intrudersEntry = get("/entries", intruderToken).list().getFirst();

        assertThat(intrudersEntry).containsEntry("status", "PLANNING");
        assertThat(intrudersEntry.get("rating")).isNull();
        // One shared cached item, two independent entries.
        assertThat(items.count()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(2);
    }

    @Test
    void anEntryIdThatDoesNotExistIsAlsoJustNotFound() {
        assertThat(get("/entries/999999", ownerToken).status()).isEqualTo(404);
    }

    private Response track(String token, String externalId) {
        return http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", externalId, "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);
    }

    private Response get(String path, String token) {
        return http.get(path, "Authorization", "Bearer " + token);
    }
}
