package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import dev.nexus.core.security.OAuthStateMismatchException;
import dev.nexus.core.security.OAuthStateStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AniListOAuthServiceTest {

    private static final String REDIRECT = "http://localhost:5173/settings/anilist/callback";
    private static final String TOKEN_URL = "https://anilist.test/oauth/token";
    private static final String API_URL = "https://anilist.test/graphql";

    private static final Long USER = 7L;

    private MockRestServiceServer server;
    private AniListOAuthService service;

    @BeforeEach
    void setUp() {
        service = build(new OAuthStateStore());
    }

    private AniListOAuthService build(OAuthStateStore states) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new AniListOAuthService(builder, properties("client-42", "shhh"), states);
    }

    @Test
    void theAuthorizationUrlCarriesTheClientAndRedirect() {
        String url = service.authorizationUrl(USER, REDIRECT);

        assertThat(url)
                .startsWith("https://anilist.test/oauth/authorize?")
                .contains("client_id=client-42")
                .contains("response_type=code")
                // Encoded, or AniList reads the query string as part of its own.
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fsettings%2Fanilist%2Fcallback");
        assertThat(stateFrom(url)).isNotBlank();
    }

    /**
     * Without this the flow is a linking CSRF: a lure carrying the attacker's code would
     * hand the attacker's AniList account to whoever was signed in here.
     */
    @Test
    void aCallbackCarryingSomebodyElsesStateIsRefusedBeforeAnyExchange() {
        service.authorizationUrl(USER, REDIRECT);

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> service.exchangeCode(USER, "lured-code", "not-the-state", REDIRECT));
        // No expectation was set: the code must never have reached AniList.
        server.verify();
    }

    /** An approval left open for a day is not one this session is still waiting for. */
    @Test
    void aStateOlderThanItsWindowIsRefused() {
        AniListOAuthService expiring = build(new OAuthStateStore(Duration.ofMinutes(-1)));
        String state = stateFrom(expiring.authorizationUrl(USER, REDIRECT));

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> expiring.exchangeCode(USER, "the-code", state, REDIRECT));
    }

    /** A code is single-use, so replaying the callback must not replay the exchange. */
    @Test
    void theStateIsGoodForOneCallbackOnly() {
        String state = stateFrom(service.authorizationUrl(USER, REDIRECT));
        expectSuccessfulExchange();

        service.exchangeCode(USER, "the-code", state, REDIRECT);

        assertThatExceptionOfType(OAuthStateMismatchException.class)
                .isThrownBy(() -> service.exchangeCode(USER, "the-code", state, REDIRECT));
        server.verify();
    }

    private void expectSuccessfulExchange() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(
                        "{\"data\":{\"Viewer\":{\"id\":7,\"name\":\"reader\"}}}", MediaType.APPLICATION_JSON));
    }

    private String stateFrom(String url) {
        for (String param : url.split("[?&]")) {
            if (param.startsWith("state=")) {
                return param.substring("state=".length());
            }
        }
        throw new IllegalStateException("No state in " + url);
    }

    /** The secret is what makes a stolen code useless, so it must never leave the server. */
    @Test
    void theCodeIsExchangedWithTheClientSecret() {
        String state = stateFrom(service.authorizationUrl(USER, REDIRECT));

        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().string(containsString("\"client_secret\":\"shhh\"")))
                .andExpect(content().string(containsString("\"grant_type\":\"authorization_code\"")))
                .andExpect(content().string(containsString("\"code\":\"the-code\"")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok\",\"refresh_token\":\"ref\",\"expires_in\":31536000}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(API_URL))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess(
                        "{\"data\":{\"Viewer\":{\"id\":7,\"name\":\"reader\"}}}", MediaType.APPLICATION_JSON));

        AniListOAuthService.Connection connection = service.exchangeCode(USER, "the-code", state, REDIRECT);

        assertThat(connection.accessToken()).isEqualTo("tok");
        assertThat(connection.refreshToken()).isEqualTo("ref");
        // The name is what a person recognises in the settings screen; the id means nothing.
        assertThat(connection.externalUserId()).isEqualTo("reader");
        assertThat(connection.expiresAt()).isAfter(Instant.now().plusSeconds(31_000_000));
        server.verify();
    }

    @Test
    void aTokenWithoutAnExpiryStillGetsOne() {
        String state = stateFrom(service.authorizationUrl(USER, REDIRECT));
        expectSuccessfulExchange();

        assertThat(service.exchangeCode(USER, "the-code", state, REDIRECT).expiresAt())
                .isAfter(Instant.now());
    }

    @Test
    void aRejectedCodeIsReportedRatherThanStoredAsAnEmptyLink() {
        String state = stateFrom(service.authorizationUrl(USER, REDIRECT));
        server.expect(requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> service.exchangeCode(USER, "stale-code", state, REDIRECT));
    }

    @Test
    void aResponseWithoutATokenIsAFailureNotASilentSuccess() {
        String state = stateFrom(service.authorizationUrl(USER, REDIRECT));
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> service.exchangeCode(USER, "stale-code", state, REDIRECT));
    }

    /** Missing credentials disable connecting, and must say so rather than build a broken URL. */
    @Test
    void withoutCredentialsConnectingIsRefusedOutright() {
        AniListOAuthService unconfigured =
                new AniListOAuthService(RestClient.builder(), properties("", ""), new OAuthStateStore());

        assertThatExceptionOfType(AniListNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.authorizationUrl(USER, REDIRECT));
        assertThatExceptionOfType(AniListNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.exchangeCode(USER, "code", "state", REDIRECT));
    }

    private static AniListProperties properties(String clientId, String clientSecret) {
        return new AniListProperties(
                API_URL, clientId, clientSecret, "https://anilist.test/oauth/authorize", TOKEN_URL, 6000, 1);
    }
}
