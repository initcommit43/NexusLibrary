package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TraktOAuthServiceTest {

    private static final TraktProperties CONFIGURED = new TraktProperties(
            "https://trakt.test",
            "client-id",
            "client-secret",
            "https://trakt.test/oauth/authorize",
            "https://trakt.test/oauth/token",
            10);

    private MockRestServiceServer server;
    private TraktOAuthService oauth;

    @BeforeEach
    void setUp() {
        oauth = build(CONFIGURED);
    }

    private TraktOAuthService build(TraktProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new TraktOAuthService(builder, properties);
    }

    @Test
    void sendsTheReaderToTraktWithAnEncodedRedirect() {
        String url = oauth.authorizationUrl("http://localhost:5173/settings/trakt/callback");

        assertThat(url)
                .isEqualTo("https://trakt.test/oauth/authorize?response_type=code&client_id=client-id"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A5173%2Fsettings%2Ftrakt%2Fcallback");
    }

    /** Trakt's token endpoint takes JSON — MAL's insistence on form encoding is MAL's alone. */
    @Test
    void exchangesTheCodeAsJson() {
        server.expect(requestTo("https://trakt.test/oauth/token"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.grant_type").value("authorization_code"))
                .andExpect(jsonPath("$.code").value("the-code"))
                .andExpect(jsonPath("$.client_secret").value("client-secret"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"fresh\",\"refresh_token\":\"renew\",\"expires_in\":7776000}",
                        MediaType.APPLICATION_JSON));

        server.expect(requestTo("https://trakt.test/users/me"))
                .andRespond(withSuccess("{\"username\":\"reader\"}", MediaType.APPLICATION_JSON));

        TraktOAuthService.Connection connection = oauth.exchangeCode("the-code", "http://localhost/cb");

        assertThat(connection.externalUserId()).isEqualTo("reader");
        assertThat(connection.accessToken()).isEqualTo("fresh");
        assertThat(connection.refreshToken()).isEqualTo("renew");
        assertThat(connection.expiresAt()).isAfter(Instant.now().plusSeconds(7_000_000));
        server.verify();
    }

    /**
     * Trakt checks the redirect on a refresh too, so it has to be the same value the code
     * exchange presented — which is why the path is a constant rather than a call-site string.
     */
    @Test
    void refreshPresentsTheSameRedirect() {
        server.expect(requestTo("https://trakt.test/oauth/token"))
                .andExpect(jsonPath("$.grant_type").value("refresh_token"))
                .andExpect(jsonPath("$.redirect_uri").value("http://localhost/cb"))
                .andRespond(withSuccess(
                        "{\"access_token\":\"fresh\",\"refresh_token\":\"rotated\",\"expires_in\":7776000}",
                        MediaType.APPLICATION_JSON));

        TraktOAuthService.Tokens tokens = oauth.refresh("old-refresh", "http://localhost/cb");

        assertThat(tokens.refreshToken()).isEqualTo("rotated");
        server.verify();
    }

    /** A refresh answered without a new refresh token means keep the old one, not lose it. */
    @Test
    void keepsTheOldRefreshTokenWhenNoNewOneComesBack() {
        server.expect(requestTo("https://trakt.test/oauth/token"))
                .andRespond(withSuccess("{\"access_token\":\"fresh\",\"expires_in\":7776000}", MediaType.APPLICATION_JSON));

        TraktOAuthService.Tokens tokens = oauth.refresh("old-refresh", "http://localhost/cb");

        assertThat(tokens.refreshToken()).isEqualTo("old-refresh");
    }

    /** No credentials disables connecting an account; it must not read as Trakt being down. */
    @Test
    void refusesToStartWithoutCredentials() {
        TraktOAuthService unconfigured =
                build(new TraktProperties("https://trakt.test", "", "", "https://trakt.test/a", "https://trakt.test/t", 10));

        assertThatExceptionOfType(TraktNotConfiguredException.class)
                .isThrownBy(() -> unconfigured.authorizationUrl("http://localhost/cb"));
    }

    @Test
    void treatsAMissingAccessTokenAsAnOutage() {
        server.expect(requestTo("https://trakt.test/oauth/token"))
                .andRespond(withSuccess("{\"error\":\"invalid_grant\"}", MediaType.APPLICATION_JSON));

        assertThatExceptionOfType(TraktUnavailableException.class)
                .isThrownBy(() -> oauth.exchangeCode("stale-code", "http://localhost/cb"));
    }
}
