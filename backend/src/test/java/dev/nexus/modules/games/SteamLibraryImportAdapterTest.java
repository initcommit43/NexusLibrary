package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SteamLibraryImportAdapterTest {

    private static final ExternalAccount ACCOUNT =
            new ExternalAccount(1L, Provider.STEAM, "76561198000000001");

    private MockRestServiceServer server;
    private SteamLibraryImportAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new SteamLibraryImportAdapter(
                builder, new SteamProperties("test-key", "https://steam.test", "https://steam.test/openid/login", 1000));
    }

    /**
     * Steam defaults to hiding "unvetted" apps: owned, played titles that never appear in
     * the response and are excluded from game_count as well. Nothing in the payload reveals
     * the omission, so this flag is the only thing standing between a user and silently
     * missing games.
     */
    @Test
    void asksSteamNotToSkipUnvettedApps() {
        server.expect(requestTo(Matchers.containsString("skip_unvetted_apps=false")))
                .andRespond(withSuccess(library(), MediaType.APPLICATION_JSON));

        adapter.pullLibrary(ACCOUNT);

        server.verify();
    }

    @Test
    void asksForPlayedFreeGamesAndAppInfo() {
        server.expect(queryParam("include_played_free_games", "true"))
                .andExpect(queryParam("include_appinfo", "true"))
                .andRespond(withSuccess(library(), MediaType.APPLICATION_JSON));

        adapter.pullLibrary(ACCOUNT);

        server.verify();
    }

    @Test
    void mapsPlaytimeIntoMinutesOfProgress() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(library(), MediaType.APPLICATION_JSON));

        List<ImportedEntry> entries = adapter.pullLibrary(ACCOUNT);

        ImportedEntry played = entries.getFirst();
        assertThat(played.itemRef().providerItemId()).isEqualTo("70");
        assertThat(played.itemRef().title()).isEqualTo("Half-Life");
        assertThat(played.progressCurrent()).isEqualTo(1237);
        assertThat(played.progressUnit()).isEqualTo(ProgressUnit.MINUTES);
        assertThat(played.status()).isEqualTo(TrackingStatus.IN_PROGRESS);
        // Playtime has no ceiling, which is why the column is nullable.
        assertThat(played.progressMax()).isNull();
    }

    @Test
    void anUnplayedGameBecomesBacklogRatherThanInProgress() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(library(), MediaType.APPLICATION_JSON));

        assertThat(adapter.pullLibrary(ACCOUNT).get(1).status()).isEqualTo(TrackingStatus.PLANNING);
    }

    /** Steam answers a private profile with an empty object rather than an error. */
    @Test
    void anEmptyResponseIsReportedAsAPrivateProfile() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess("{\"response\":{}}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(SteamProfilePrivateException.class)
                .isThrownBy(() -> adapter.pullLibrary(ACCOUNT));
    }

    @Test
    void aMissingApiKeyIsReportedBeforeAnyRequestIsMade() {
        SteamLibraryImportAdapter unconfigured = new SteamLibraryImportAdapter(
                RestClient.builder(), new SteamProperties("", "https://steam.test", "https://steam.test/openid/login", 1000));

        assertThatExceptionOfType(SteamUnavailableException.class)
                .isThrownBy(() -> unconfigured.pullLibrary(ACCOUNT));
    }

    private String library() {
        return """
            {"response":{"game_count":2,"games":[
              {"appid":70,"name":"Half-Life","playtime_forever":1237},
              {"appid":220,"name":"Half-Life 2","playtime_forever":0}
            ]}}""";
    }
}
