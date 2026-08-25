package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Pins the requests actually sent to Simkl, and what its refusals turn into on this side. */
class SimklClientTest {

    private static final SimklProperties CONFIGURED = new SimklProperties(
            "https://simkl.test",
            "client-id",
            "client-secret",
            "https://simkl.test/oauth/authorize",
            "https://simkl.test/oauth/token",
            "nexus-media-tracker",
            "1.0",
            100);

    private static final ExternalAccount ACCOUNT = account();

    private static ExternalAccount account() {
        ExternalAccount account = new ExternalAccount(1L, Provider.SIMKL, "reader");
        account.setAccessToken("token");
        return account;
    }

    private MockRestServiceServer server;
    private SimklClient client;

    @BeforeEach
    void setUp() {
        client = build(CONFIGURED);
    }

    private SimklClient build(SimklProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new SimklClient(builder, properties);
    }

    /** Every Simkl call identifies the application, not only the reader. */
    @Test
    void identifiesBothTheReaderAndTheApp() {
        server.expect(requestTo(Matchers.allOf(
                        Matchers.startsWith("https://simkl.test/sync/all-items/movies"),
                        Matchers.containsString("extended=full"),
                        Matchers.containsString("client_id=client-id"),
                        Matchers.containsString("app-name=nexus-media-tracker"),
                        Matchers.containsString("app-version=1.0"))))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(header("User-Agent", "nexus-media-tracker/1.0"))
                .andRespond(withSuccess("{\"movies\":[{\"status\":\"completed\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.movies(ACCOUNT)).hasSize(1);
        server.verify();
    }

    @Test
    void readsShowsFromTheirOwnKey() {
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/sync/all-items/shows")))
                .andRespond(withSuccess(
                        "{\"shows\":[{\"status\":\"watching\"},{\"status\":\"hold\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.shows(ACCOUNT)).hasSize(2);
    }

    /** An empty library answers with {}, not with an empty list under the key. */
    @Test
    void anEmptyLibraryIsEmptyRatherThanAnError() {
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/sync/all-items/movies")))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.movies(ACCOUNT)).isEmpty();
    }

    /**
     * A Simkl token has no expiry, so a refusal means the reader revoked the app — advice,
     * not an outage to retry.
     */
    @Test
    void aRefusedTokenAsksForAReconnectRatherThanARetry() {
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/sync/all-items/movies")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatExceptionOfType(SimklReconnectRequiredException.class).isThrownBy(() -> client.movies(ACCOUNT));
    }

    @Test
    void aServerFaultIsAnOutage() {
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/sync/all-items/shows")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatExceptionOfType(SimklUnavailableException.class).isThrownBy(() -> client.shows(ACCOUNT));
    }

    @Test
    void saysSoWhenNoCredentialsAreConfigured() {
        SimklClient unconfigured = build(new SimklProperties(
                "https://simkl.test", "", "", "https://simkl.test/a", "https://simkl.test/t", "app", "1.0", 100));

        assertThatExceptionOfType(SimklNotConfiguredException.class).isThrownBy(() -> unconfigured.movies(ACCOUNT));
    }
}
