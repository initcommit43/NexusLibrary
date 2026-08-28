package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Switching a module off, and keeping one reader's switches away from another's. */
class ModulePreferenceIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String token;
    private String othersToken;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");
        othersToken = registerAndGetToken(http, "other@example.com", "other");
    }

    @SuppressWarnings("unchecked")
    private List<String> disabledFor(String forToken) {
        Response response = http.get("/settings/modules", "Authorization", "Bearer " + forToken);
        assertThat(response.status()).isEqualTo(200);
        return (List<String>) response.body().get("disabled");
    }

    private Response put(String forToken, List<String> disabled) {
        return http.putJson(
                "/settings/modules",
                Map.of("disabled", disabled),
                "Authorization",
                "Bearer " + forToken);
    }

    /** Nothing written means everything on, so a new reader costs no rows to have them all. */
    @Test
    void everythingIsOnUntilSomethingIsSwitchedOff() {
        assertThat(disabledFor(token)).isEmpty();
    }

    @Test
    void switchingModulesOffIsRemembered() {
        assertThat(put(token, List.of("BOOK", "GAME")).status()).isEqualTo(200);

        assertThat(disabledFor(token)).containsExactlyInAnyOrder("BOOK", "GAME");
    }

    /**
     * The client sends the state of every switch at once, so a second write is the whole
     * answer and not an addition to the first.
     */
    @Test
    void writingAgainReplacesRatherThanAdds() {
        put(token, List.of("BOOK", "GAME"));
        put(token, List.of("MANGA"));

        assertThat(disabledFor(token)).containsExactly("MANGA");
    }

    @Test
    void switchingEverythingBackOnLeavesNothingBehind() {
        put(token, List.of("BOOK"));
        put(token, List.of());

        assertThat(disabledFor(token)).isEmpty();
    }

    /** The endpoint takes no id, so one reader's switches are unreachable from another's. */
    @Test
    void oneReadersSwitchesAreTheirOwn() {
        put(token, List.of("BOOK", "GAME"));

        assertThat(disabledFor(othersToken)).isEmpty();
        assertThat(disabledFor(token)).containsExactlyInAnyOrder("BOOK", "GAME");
    }

    @Test
    void signedOutCallersAreRefused() {
        assertThat(http.get("/settings/modules").status()).isEqualTo(401);
    }
}
