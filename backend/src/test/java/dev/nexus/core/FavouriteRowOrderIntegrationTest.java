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

/** The order a reader drags their favourite rows into, and whose order it stays. */
class FavouriteRowOrderIntegrationTest extends PostgresIntegrationTest {

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

    /** Nothing written means the app's own order, so a new reader costs no rows to have one. */
    @Test
    void thereIsNoOrderUntilTheReaderArrangesOne() {
        assertThat(orderFor(token)).isEmpty();
    }

    @Test
    void theArrangementIsRememberedInTheOrderItWasSent() {
        assertThat(put(token, List.of("MANGA", "ANIME", "GAME")).status()).isEqualTo(200);

        assertThat(orderFor(token)).containsExactly("MANGA", "ANIME", "GAME");
    }

    @Test
    void arrangingAgainReplacesTheWholeOrder() {
        put(token, List.of("MANGA", "ANIME", "GAME"));
        put(token, List.of("GAME", "MANGA"));

        assertThat(orderFor(token)).containsExactly("GAME", "MANGA");
    }

    /** A row named twice is one row; the first place it was given is the one it keeps. */
    @Test
    void aRepeatedRowKeepsItsFirstPlace() {
        put(token, List.of("ANIME", "GAME", "ANIME"));

        assertThat(orderFor(token)).containsExactly("ANIME", "GAME");
    }

    @Test
    void anEmptyArrangementPutsTheRowsBackInTheAppsOwnOrder() {
        put(token, List.of("MANGA", "ANIME"));

        assertThat(put(token, List.of()).status()).isEqualTo(200);
        assertThat(orderFor(token)).isEmpty();
    }

    @Test
    void oneReadersArrangementIsNotAnothers() {
        put(token, List.of("MANGA", "ANIME"));
        put(othersToken, List.of("BOOK", "MOVIE"));

        assertThat(orderFor(token)).containsExactly("MANGA", "ANIME");
        assertThat(orderFor(othersToken)).containsExactly("BOOK", "MOVIE");
    }

    /** Two rows sharing a band, which is what dropping one onto another makes. */
    @Test
    void aRowCanBeToldToSitBesideTheOneBeforeIt() {
        assertThat(put(token, List.of("ANIME", "MANGA", "GAME"), List.of("MANGA")).status())
                .isEqualTo(200);

        assertThat(pairedFor(token)).containsExactly("MANGA");
    }

    /** There is nothing before the first row for it to sit beside. */
    @Test
    void theFirstRowIsNeverPaired() {
        put(token, List.of("ANIME", "MANGA"), List.of("ANIME"));

        assertThat(pairedFor(token)).isEmpty();
    }

    /** A band holds two: the third of a run is left standing on its own beneath them. */
    @Test
    void aThirdRowCannotJoinAPairThatIsAlreadyMade() {
        put(token, List.of("ANIME", "MANGA", "GAME"), List.of("MANGA", "GAME"));

        assertThat(pairedFor(token)).containsExactly("MANGA");
    }

    @Test
    void splittingAPairLeavesBothRowsStandingAlone() {
        put(token, List.of("ANIME", "MANGA"), List.of("MANGA"));

        assertThat(put(token, List.of("ANIME", "MANGA"), List.of()).status()).isEqualTo(200);
        assertThat(pairedFor(token)).isEmpty();
    }

    /** Rearranging without saying anything about pairs is a rearrangement, not an unpairing. */
    @Test
    void anArrangementWithNoPairsNamedHasNone() {
        put(token, List.of("ANIME", "MANGA"), List.of("MANGA"));
        put(token, List.of("MANGA", "ANIME"));

        assertThat(pairedFor(token)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<String> pairedFor(String forToken) {
        Response response = http.get("/settings/favourite-rows", "Authorization", "Bearer " + forToken);
        assertThat(response.status()).isEqualTo(200);
        return (List<String>) response.body().get("paired");
    }

    @SuppressWarnings("unchecked")
    private List<String> orderFor(String forToken) {
        Response response = http.get("/settings/favourite-rows", "Authorization", "Bearer " + forToken);
        assertThat(response.status()).isEqualTo(200);
        return (List<String>) response.body().get("order");
    }

    private Response put(String forToken, List<String> order) {
        return put(forToken, order, List.of());
    }

    private Response put(String forToken, List<String> order, List<String> paired) {
        return http.putJson(
                "/settings/favourite-rows",
                Map.of("order", order, "paired", paired),
                "Authorization",
                "Bearer " + forToken);
    }
}
