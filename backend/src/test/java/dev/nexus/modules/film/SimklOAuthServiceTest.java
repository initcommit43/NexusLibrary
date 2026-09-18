package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.nexus.core.security.OAuthStateMismatchException;
import dev.nexus.core.security.OAuthStateStore;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class SimklOAuthServiceTest {

    private static final SimklProperties CONFIGURED = new SimklProperties(
            "https://simkl.test",
            "client-id",
            "client-secret",
            "https://simkl.test/oauth/authorize",
            "https://simkl.test/oauth/token",
            "nexus-media-tracker",
            "1.0",
            10);

    private static final Long USER = 7L;
    private static final String REDIRECT = "http://localhost:5173/settings/simkl/callback";

    private MockRestServiceServer server;
    private SimklOAuthService oauth;

    @BeforeEach
    void setUp() {
        oauth = build(CONFIGURED, new OAuthStateStore());
    }

    private SimklOAuthService build(SimklProperties properties, OAuthStateStore states) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new SimklOAuthService(builder, properties, states);
    }

    /** Simkl wants the app named at authorization as well as on every later call. */
    @Test
    void sendsTheReaderToSimklIdentifyingTheApp() {
        String url = oauth.authorizationUrl(USER, REDIRECT);

        assertThat(url)
                .startsWith("https://simkl.test/oauth/authorize?response_type=code&client_id=client-id"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fsettings%2Fsimkl%2Fcallback"
                        + "&app-name=nexus-media-tracker&app-version=1.0&state=");
        assertThat(stateFrom(url)).isNotBlank();
    }

    /**
     * Without this the flow is a linking CSRF: a lure carrying the attacker's code would
     * hand the attacker's Simkl account to whoever was signed in here.
     */
    @Test
    void aCallbackCarryingSomebodyElsesStateIsRefusedBeforeAnyExchange() {
        oauth.authorizationUrl(USER, REDIRECT);

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> oauth.exchangeCode(USER, "lured-code", "not-the-state", REDIRECT));
        // No expectation was set: the code must never have reached Simkl.
        server.verify();
    }

    /** An approval left open for a day is not one this session is still waiting for. */
    @Test
    void aStateOlderThanItsWindowIsRefused() {
        SimklOAuthService expiring = build(CONFIGURED, new OAuthStateStore(Duration.ofMinutes(-1)));
        String state = stateFrom(expiring.authorizationUrl(USER, REDIRECT));

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> expiring.exchangeCode(USER, "the-code", state, REDIRECT));
    }

    /** A code is single-use, so replaying the callback must not replay the exchange. */
    @Test
    void theStateIsGoodForOneCallbackOnly() {
        String state = stateFrom(oauth.authorizationUrl(USER, REDIRECT));
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/oauth/token")))
                .andRespond(withSuccess("{\"access_token\":\"fresh\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/users/settings")))
                .andRespond(withSuccess("{\"user\":{\"name\":\"reader\"}}", MediaType.APPLICATION_JSON));

        oauth.exchangeCode(USER, "the-code", state, REDIRECT);

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> oauth.exchangeCode(USER, "the-code", state, REDIRECT));
        server.verify();
    }

    private String stateFrom(String url) {
        for (String param : url.split("[?&]")) {
            if (param.startsWith("state=")) {
                return param.substring("state=".length());
            }
        }
        throw new IllegalStateException("No state in " + url);
    }

    @Test
    void exchangesTheCodeAsJsonWithTheAppIdentifiedInTheQuery() {
        String state = stateFrom(oauth.authorizationUrl(USER, REDIRECT));

        server.expect(requestTo(Matchers.startsWith("https://simkl.test/oauth/token?client_id=client-id")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.grant_type").value("authorization_code"))
                .andExpect(jsonPath("$.code").value("the-code"))
                .andExpect(jsonPath("$.client_secret").value("client-secret"))
                .andRespond(withSuccess("{\"access_token\":\"fresh\",\"token_type\":\"bearer\"}", MediaType.APPLICATION_JSON));

        server.expect(requestTo(Matchers.startsWith("https://simkl.test/users/settings")))
                .andExpect(header("Authorization", "Bearer fresh"))
                .andRespond(withSuccess("{\"user\":{\"name\":\"reader\"},\"account\":{\"id\":1}}", MediaType.APPLICATION_JSON));

        SimklOAuthService.Connection connection = oauth.exchangeCode(USER, "the-code", state, REDIRECT);

        assertThat(connection.externalUserId()).isEqualTo("reader");
        assertThat(connection.accessToken()).isEqualTo("fresh");
        server.verify();
    }

    /** The settings endpoint is a POST, which is Simkl's convention rather than a slip here. */
    @Test
    void asksWhoTheTokenBelongsToWithAPost() {
        String state = stateFrom(oauth.authorizationUrl(USER, REDIRECT));
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/oauth/token")))
                .andRespond(withSuccess("{\"access_token\":\"fresh\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/users/settings")))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.method(
                        org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"user\":{\"name\":\"reader\"}}", MediaType.APPLICATION_JSON));

        oauth.exchangeCode(USER, "the-code", state, REDIRECT);

        server.verify();
    }

    @Test
    void treatsAMissingAccessTokenAsAnOutage() {
        String state = stateFrom(oauth.authorizationUrl(USER, REDIRECT));
        server.expect(requestTo(Matchers.startsWith("https://simkl.test/oauth/token")))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(SimklUnavailableException.class)
                .isThrownBy(() -> oauth.exchangeCode(USER, "stale-code", state, REDIRECT));
    }

    /** No credentials disables connecting an account; it must not read as Simkl being down. */
    @Test
    void refusesToStartWithoutCredentials() {
        SimklOAuthService unconfigured = build(
                new SimklProperties(
                        "https://simkl.test", "", "", "https://simkl.test/a", "https://simkl.test/t", "app", "1.0", 10),
                new OAuthStateStore());

        assertThatExceptionOfType(SimklNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.authorizationUrl(USER, REDIRECT));
    }
}
