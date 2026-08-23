package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

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

    private MockRestServiceServer server;
    private AniListOAuthService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new AniListOAuthService(builder, properties("client-42", "shhh"));
    }

    @Test
    void theAuthorizationUrlCarriesTheClientAndRedirect() {
        String url = service.authorizationUrl(REDIRECT);

        assertThat(url)
                .startsWith("https://anilist.test/oauth/authorize?")
                .contains("client_id=client-42")
                .contains("response_type=code")
                // Encoded, or AniList reads the query string as part of its own.
                .contains("redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fsettings%2Fanilist%2Fcallback");
    }

    /** The secret is what makes a stolen code useless, so it must never leave the server. */
    @Test
    void theCodeIsExchangedWithTheClientSecret() {
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

        AniListOAuthService.Connection connection = service.exchangeCode("the-code", REDIRECT);

        assertThat(connection.accessToken()).isEqualTo("tok");
        assertThat(connection.refreshToken()).isEqualTo("ref");
        // The name is what a person recognises in the settings screen; the id means nothing.
        assertThat(connection.externalUserId()).isEqualTo("reader");
        assertThat(connection.expiresAt()).isAfter(Instant.now().plusSeconds(31_000_000));
        server.verify();
    }

    @Test
    void aTokenWithoutAnExpiryStillGetsOne() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"access_token\":\"tok\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(API_URL))
                .andRespond(withSuccess(
                        "{\"data\":{\"Viewer\":{\"id\":7,\"name\":\"reader\"}}}", MediaType.APPLICATION_JSON));

        assertThat(service.exchangeCode("the-code", REDIRECT).expiresAt()).isAfter(Instant.now());
    }

    @Test
    void aRejectedCodeIsReportedRatherThanStoredAsAnEmptyLink() {
        server.expect(requestTo(TOKEN_URL)).andRespond(withUnauthorizedRequest());

        assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> service.exchangeCode("stale-code", REDIRECT));
    }

    @Test
    void aResponseWithoutATokenIsAFailureNotASilentSuccess() {
        server.expect(requestTo(TOKEN_URL))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(AniListUnavailableException.class)
                .isThrownBy(() -> service.exchangeCode("stale-code", REDIRECT));
    }

    /** Missing credentials disable connecting, and must say so rather than build a broken URL. */
    @Test
    void withoutCredentialsConnectingIsRefusedOutright() {
        AniListOAuthService unconfigured =
                new AniListOAuthService(RestClient.builder(), properties("", ""));

        assertThatExceptionOfType(AniListNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.authorizationUrl(REDIRECT));
        assertThatExceptionOfType(AniListNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.exchangeCode("code", REDIRECT));
    }

    private static AniListProperties properties(String clientId, String clientSecret) {
        return new AniListProperties(
                API_URL, clientId, clientSecret, "https://anilist.test/oauth/authorize", TOKEN_URL, 6000, 1);
    }
}
