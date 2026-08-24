package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Pins where MAL's flow leaves AniList's: PKCE is mandatory and only {@code plain}, the
 * token endpoint takes forms rather than JSON, and a refresh answer may omit the refresh
 * token — which means keep the old one, not lose it. These are exactly the places a copy
 * of the AniList service would have failed.
 */
class MalOAuthServiceTest {

    private static final String TOKEN_URL = "https://mal.test/oauth2/token";
    private static final String REDIRECT = "http://localhost:5173/settings/mal/callback";

    private MockRestServiceServer server;
    private MalOAuthService oauth;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        oauth = new MalOAuthService(
                builder,
                new MalProperties(
                        "https://mal.test/v2",
                        "test-mal-client",
                        "test-mal-secret",
                        "https://mal.test/oauth2/authorize",
                        TOKEN_URL,
                        6000,
                        1));
    }

    /** With the {@code plain} method the challenge in the URL is the verifier itself. */
    @Test
    void theAuthorizationUrlCarriesAPlainPkceChallenge() {
        String url = oauth.authorizationUrl(7L, REDIRECT);

        assertThat(url).contains("code_challenge_method=plain");
        assertThat(url).contains("response_type=code");
        assertThat(challengeFrom(url)).hasSizeGreaterThanOrEqualTo(43).hasSizeLessThanOrEqualTo(128);
    }

    /**
     * The exchange must present the verifier minted at authorize time, form-encoded. A
     * copy of the AniList exchange would have sent JSON with no verifier — two refusals
     * in one request.
     */
    @Test
    void theExchangeSendsTheMintedVerifierAsAForm() {
        String verifier = challengeFrom(oauth.authorizationUrl(7L, REDIRECT));

        server.expect(requestTo(TOKEN_URL))
                .andExpect(header("Content-Type", Matchers.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andExpect(content().formData(form(verifier)))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok\",\"refresh_token\":\"ref\",\"expires_in\":2678400}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith("https://mal.test/v2/users/@me")))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("{\"id\":1,\"name\":\"reader\"}", MediaType.APPLICATION_JSON));

        MalOAuthService.Connection connection = oauth.exchangeCode(7L, "the-code", REDIRECT);

        assertThat(connection.externalUserId()).isEqualTo("reader");
        assertThat(connection.accessToken()).isEqualTo("tok");
        assertThat(connection.refreshToken()).isEqualTo("ref");
        assertThat(connection.expiresAt()).isAfter(Instant.now());
        server.verify();
    }

    /** A verifier is single-use: replaying a callback must not replay the exchange. */
    @Test
    void aCallbackWithoutALiveVerifierIsRefused() {
        assertThatExceptionOfType(MalAuthorizationExpiredException.class)
                .isThrownBy(() -> oauth.exchangeCode(7L, "stale-code", REDIRECT));
    }

    /** Starting over replaces the pending verifier; only the newest attempt can finish. */
    @Test
    void onlyTheNewestAuthorizationAttemptCanFinish() {
        String first = challengeFrom(oauth.authorizationUrl(7L, REDIRECT));
        String second = challengeFrom(oauth.authorizationUrl(7L, REDIRECT));
        assertThat(second).isNotEqualTo(first);

        server.expect(requestTo(TOKEN_URL))
                .andExpect(content().formData(form(second)))
                .andRespond(withSuccess(
                        "{\"access_token\":\"tok\",\"expires_in\":100}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.startsWith("https://mal.test/v2/users/@me")))
                .andRespond(withSuccess("{\"name\":\"reader\"}", MediaType.APPLICATION_JSON));

        oauth.exchangeCode(7L, "the-code", REDIRECT);

        server.verify();
    }

    /** MAL may answer a refresh without a refresh token; that means the old one still holds. */
    @Test
    void aRefreshWithoutANewRefreshTokenKeepsTheOldOne() {
        server.expect(requestTo(TOKEN_URL))
                .andExpect(header("Content-Type", Matchers.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)))
                .andRespond(withSuccess(
                        "{\"access_token\":\"fresh\",\"expires_in\":2678400}", MediaType.APPLICATION_JSON));

        MalOAuthService.Tokens tokens = oauth.refresh("old-refresh");

        assertThat(tokens.accessToken()).isEqualTo("fresh");
        assertThat(tokens.refreshToken()).isEqualTo("old-refresh");
        server.verify();
    }

    private org.springframework.util.MultiValueMap<String, String> form(String verifier) {
        org.springframework.util.LinkedMultiValueMap<String, String> form =
                new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", "the-code");
        form.add("client_id", "test-mal-client");
        form.add("redirect_uri", REDIRECT);
        form.add("code_verifier", verifier);
        form.add("client_secret", "test-mal-secret");
        return form;
    }

    private static String challengeFrom(String url) {
        for (String param : url.split("[?&]")) {
            if (param.startsWith("code_challenge=")) {
                return URLDecoder.decode(param.substring("code_challenge=".length()), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("No code_challenge in " + url);
    }
}
