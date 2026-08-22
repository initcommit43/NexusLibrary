package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SteamAchievementsClientTest {

    private static final String APP_ID = "1086940";
    private static final String STEAM_ID = "76561198000000001";

    private MockRestServiceServer server;
    private SteamAchievementsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SteamAchievementsClient(
                builder,
                new SteamProperties("test-key", "https://steam.test", "https://steam.test/openid/login", 1000),
                // The retry logic is what matters here, not how long the real delays are.
                Duration.ofMillis(1));
    }

    @Test
    void readsUnlockedAndLockedAchievements() {
        server.expect(requestTo(Matchers.containsString("appid=" + APP_ID)))
                .andRespond(withSuccess(twoAchievements(), MediaType.APPLICATION_JSON));

        List<Map<String, Object>> achievements = client.fetch(APP_ID, STEAM_ID).orElseThrow();

        assertThat(achievements).hasSize(2);
        assertThat(achievements.getFirst()).containsEntry("apiname", "FIRST_BLOOD");
        server.verify();
    }

    /**
     * A different privacy setting from the one the library needs, so it must surface as its
     * own failure — telling someone to change the wrong setting sends them in circles.
     */
    @Test
    void aPrivateProfileIsReportedDistinctly() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(
                        "{\"playerstats\":{\"error\":\"Profile is not public\",\"success\":false}}",
                        MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(SteamProfileNotPublicException.class)
                .isThrownBy(() -> client.fetch(APP_ID, STEAM_ID));
    }

    /** Plenty of games simply have none. That is a fact about the game, not a failure. */
    @Test
    void aGameWithoutAchievementsIsEmptyRatherThanAnError() {
        server.expect(requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(
                        "{\"playerstats\":{\"error\":\"Requested app has no stats\",\"success\":false}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.fetch(APP_ID, STEAM_ID)).isEmpty();
    }

    @Test
    void aBadRequestForAnAppWithNoStatsIsAlsoEmpty() {
        server.expect(requestTo(Matchers.any(String.class))).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThat(client.fetch(APP_ID, STEAM_ID)).isEmpty();
    }

    /** Steam applies undocumented per-method limits; one throttled call must not end a sync. */
    @Test
    void retriesAfterBeingThrottled() {
        server.expect(ExpectedCount.once(), requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(ExpectedCount.once(), requestTo(Matchers.any(String.class)))
                .andRespond(withSuccess(twoAchievements(), MediaType.APPLICATION_JSON));

        assertThat(client.fetch(APP_ID, STEAM_ID).orElseThrow()).hasSize(2);
        server.verify();
    }

    /**
     * Reported as throttling rather than a general outage: whatever synced before this point
     * is committed, and the answer is to run again shortly rather than to investigate.
     */
    @Test
    void sustainedThrottlingIsReportedAsThrottlingNotAnOutage() {
        server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.any(String.class)))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatExceptionOfType(SteamThrottledException.class)
                .isThrownBy(() -> client.fetch(APP_ID, STEAM_ID));
    }

    @Test
    void aMissingApiKeyIsReportedBeforeAnyRequest() {
        SteamAchievementsClient unconfigured = new SteamAchievementsClient(
                RestClient.builder(), new SteamProperties("", "https://steam.test", "https://steam.test/openid", 1000));

        assertThatExceptionOfType(SteamUnavailableException.class)
                .isThrownBy(() -> unconfigured.fetch(APP_ID, STEAM_ID));
    }

    private String twoAchievements() {
        return """
            {"playerstats":{"steamID":"1","gameName":"Test","success":true,"achievements":[
              {"apiname":"FIRST_BLOOD","achieved":1,"unlocktime":1700000000,
               "name":"First Blood","description":"Win a fight"},
              {"apiname":"COMPLETIONIST","achieved":0,"unlocktime":0,
               "name":"Completionist","description":"Finish everything"}
            ]}}""";
    }
}
