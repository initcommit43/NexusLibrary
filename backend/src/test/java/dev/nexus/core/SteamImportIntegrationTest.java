package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ExternalAccountRepository;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.importing.ExternalAccountService;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.modules.games.SteamLibraryImportAdapter;
import dev.nexus.modules.games.SteamProfilePrivateException;
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
 * The Steam import path end to end, with both external services stubbed: what Steam reports
 * as the library, and IGDB's Steam cross-reference.
 */
class SteamImportIntegrationTest extends PostgresIntegrationTest {

    private static final String STEAM_ID = "76561198000000001";
    private static final String KNOWN_APPID = "70";
    private static final String UNKNOWN_APPID = "999999";
    private static final String IGDB_ID = "7346";

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    SteamLibraryImportAdapter steamAdapter;

    @Autowired
    AppUserRepository users;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    ExternalAccountRepository accounts;

    @Autowired
    ExternalAccountService accountService;

    private HttpTestClient http;
    private String token;
    private Long userId;

    @BeforeEach
    void setUp() {
        entries.deleteAll();
        accounts.deleteAll();
        items.deleteAll();
        users.deleteAll();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "player@example.com", "player");
        userId = users.findByEmail("player@example.com").orElseThrow().getId();

        when(steamAdapter.provider()).thenReturn(Provider.STEAM);
        // Steam appid 70 cross-references to IGDB game 7346; 999999 has no counterpart.
        when(igdbClient.findGamesBySteamAppIds(anyCollection()))
                .thenReturn(List.of(Map.of("uid", KNOWN_APPID, "game", Map.of("id", 7346))));
        when(igdbClient.findGamesByIds(anyCollection())).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
    }

    @Test
    void importResolvesPlaytimeIntoATrackedEntry() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 5400));

        Response report = runImport();

        assertThat(report.status()).isEqualTo(200);
        assertThat(report.body()).containsEntry("created", 1);
        assertThat(report.body()).containsEntry("updated", 0);

        Map<String, Object> entry = http.get("/entries", "Authorization", "Bearer " + token)
                .list()
                .getFirst();
        assertThat(entry).containsEntry("status", "IN_PROGRESS");
        assertThat(entry).containsEntry("progressCurrent", 5400);
        assertThat(entry).containsEntry("progressUnit", "MINUTES");
        // Playtime has no ceiling, which is why the column is nullable.
        assertThat(entry.get("progressMax")).isNull();
    }

    @Test
    void anUnplayedGameLandsInTheBacklogRatherThanInProgress() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 0));

        runImport();

        assertThat(http.get("/entries", "Authorization", "Bearer " + token)
                        .list()
                        .getFirst())
                .containsEntry("status", "PLANNING");
    }

    @Test
    void titlesWithNoCounterpartAreReportedRatherThanDropped() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 120), playedFor(UNKNOWN_APPID, 60));

        Response report = runImport();

        assertThat(report.body()).containsEntry("created", 1);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unmatched = (List<Map<String, Object>>) report.body().get("unmatched");
        assertThat(unmatched).hasSize(1);
        assertThat(unmatched.getFirst()).containsEntry("providerItemId", UNKNOWN_APPID);
        assertThat(unmatched.getFirst().get("title")).isNotNull();
    }

    @Test
    void reimportingUpdatesProgressInsteadOfDuplicating() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 100));
        runImport();

        givenLibrary(playedFor(KNOWN_APPID, 250));
        Response second = runImport();

        assertThat(second.body()).containsEntry("created", 0);
        assertThat(second.body()).containsEntry("updated", 1);
        assertThat(entries.count()).isEqualTo(1);
        assertThat(http.get("/entries", "Authorization", "Bearer " + token)
                        .list()
                        .getFirst())
                .containsEntry("progressCurrent", 250);
    }

    /** An import reports objective playtime; it must not overwrite the user's own judgement. */
    @Test
    void reimportingDoesNotOverwriteARatingTheUserSet() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 100));
        runImport();

        long entryId = ((Number) http.get("/entries", "Authorization", "Bearer " + token)
                        .list()
                        .getFirst()
                        .get("id"))
                .longValue();
        http.patchJson("/entries/" + entryId, Map.of("rating", 88), "Authorization", "Bearer " + token);

        givenLibrary(playedFor(KNOWN_APPID, 300));
        runImport();

        assertThat(http.get("/entries", "Authorization", "Bearer " + token)
                        .list()
                        .getFirst())
                .containsEntry("rating", 88);
    }

    /** The cache claim again, now on the import path. */
    @Test
    void aSecondUserImportingTheSameGameCostsNoExtraCatalogueFetch() {
        connect();
        givenLibrary(playedFor(KNOWN_APPID, 100));
        runImport();
        verify(igdbClient, times(1)).findGamesByIds(anyCollection());

        String otherToken = registerAndGetToken(http, "second@example.com", "second");
        Long otherId = users.findByEmail("second@example.com").orElseThrow().getId();
        accountService.connect(otherId, Provider.STEAM, "76561198000000002");
        http.post("/integrations/STEAM/import", "Authorization", "Bearer " + otherToken);

        // Still one fetch and one cached row, now backing two users' entries.
        verify(igdbClient, times(1)).findGamesByIds(anyCollection());
        assertThat(items.count()).isEqualTo(1);
        assertThat(entries.count()).isEqualTo(2);
    }

    @Test
    void aPrivateProfileIsReportedAsSomethingTheUserCanFix() {
        connect();
        when(steamAdapter.pullLibrary(any())).thenThrow(new SteamProfilePrivateException());

        Response response = runImport();

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.body().get("message").toString()).containsIgnoringCase("public");
    }

    @Test
    void importingWithoutAConnectedAccountIsRejected() {
        assertThat(runImport().status()).isEqualTo(404);
    }

    @Test
    void aConnectedAccountIsVisibleToItsOwnerOnly() {
        connect();

        List<Map<String, Object>> mine =
                http.get("/integrations", "Authorization", "Bearer " + token).list();
        assertThat(mine).hasSize(1);
        assertThat(mine.getFirst()).containsEntry("provider", "STEAM");

        String otherToken = registerAndGetToken(http, "other@example.com", "other");
        assertThat(http.get("/integrations", "Authorization", "Bearer " + otherToken).list())
                .isEmpty();
    }

    @Test
    void anotherUserCannotDisconnectYourAccount() {
        connect();
        String otherToken = registerAndGetToken(http, "other@example.com", "other");

        assertThat(http.delete("/integrations/STEAM", "Authorization", "Bearer " + otherToken)
                        .status())
                .isEqualTo(404);
        assertThat(accounts.count()).isEqualTo(1);
    }

    @Test
    void reconnectingRepointsTheExistingLinkRatherThanAddingASecond() {
        connect();
        accountService.connect(userId, Provider.STEAM, "76561198000000002");

        assertThat(accounts.findByUserId(userId)).hasSize(1);
        assertThat(accounts.findByUserIdAndProvider(userId, Provider.STEAM)
                        .orElseThrow()
                        .getExternalUserId())
                .isEqualTo("76561198000000002");
    }

    @Test
    void everyIntegrationEndpointRequiresAuthentication() {
        assertThat(http.get("/integrations").status()).isEqualTo(401);
        assertThat(http.post("/integrations/STEAM/import").status()).isEqualTo(401);
        assertThat(http.post("/integrations/steam/authorize").status()).isEqualTo(401);
        assertThat(http.delete("/integrations/STEAM").status()).isEqualTo(401);
    }

    @Test
    void nothingIsPulledForAnUnauthenticatedCaller() {
        http.post("/integrations/STEAM/import");

        verify(steamAdapter, never()).pullLibrary(any());
    }

    private void connect() {
        accountService.connect(userId, Provider.STEAM, STEAM_ID);
    }

    private void givenLibrary(ImportedEntry... library) {
        when(steamAdapter.pullLibrary(any())).thenReturn(List.of(library));
    }

    private ImportedEntry playedFor(String appId, int minutes) {
        return new ImportedEntry(
                new ExternalItemRef(Provider.STEAM, appId, "Game " + appId),
                minutes > 0 ? TrackingStatus.IN_PROGRESS : TrackingStatus.PLANNING,
                minutes,
                null,
                ProgressUnit.MINUTES,
                null,
                null,
                null,
                null);
    }

    private Response runImport() {
        return http.post("/integrations/STEAM/import", "Authorization", "Bearer " + token);
    }
}
