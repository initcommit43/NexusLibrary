package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.importing.ExternalAccountService;
import java.time.Instant;
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

class MalClientTest {

    private static final String BASE = "https://mal.test/v2";

    private MockRestServiceServer server;
    private MalClient client;
    private MalOAuthService oauth;
    private ExternalAccountService accounts;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        oauth = mock(MalOAuthService.class);
        accounts = mock(ExternalAccountService.class);
        client = new MalClient(
                builder,
                new MalProperties(
                        BASE,
                        "test-mal-client",
                        "test-mal-secret",
                        "https://mal.test/oauth2/authorize",
                        "https://mal.test/oauth2/token",
                        6000,
                        1),
                oauth,
                accounts);
    }

    private ExternalAccount account(Instant expiresAt) {
        ExternalAccount account = new ExternalAccount(7L, Provider.MAL, "reader");
        account.setAccessToken("live-token");
        account.setRefreshToken("refresh-token");
        account.setTokenExpiresAt(expiresAt);
        return account;
    }

    private ExternalAccount freshAccount() {
        return account(Instant.now().plusSeconds(3600));
    }

    /** Lists are the reader's own, read as {@code @me} with their token — never by name. */
    @Test
    void listsAreReadAsTheSignedInUser() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/@me/animelist")))
                .andExpect(header("Authorization", "Bearer live-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.fetchAnimeList(freshAccount());

        verify(oauth, never()).refresh(anyString());
        server.verify();
    }

    /** By default MAL quietly withholds entries it rates as adult; a list must come whole. */
    @Test
    void listsAreRequestedWithNothingWithheld() {
        server.expect(requestTo(Matchers.startsWith(BASE + "/users/@me/mangalist")))
                .andExpect(queryParam("nsfw", "true"))
                // The braces travel percent-encoded, as URI query characters must.
                .andExpect(queryParam("fields", MalClient.MANGA_FIELDS.replace("{", "%7B").replace("}", "%7D")))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.fetchMangaList(freshAccount());

        server.verify();
    }

    /** MAL pages by a hundred; a longer list must be followed to its end, not truncated. */
    @Test
    void pagingIsFollowedUntilMalStopsOfferingMore() {
        server.expect(requestTo(Matchers.containsString("offset=0")))
                .andRespond(withSuccess(
                        "{\"data\":[{\"node\":{\"id\":1}}],\"paging\":{\"next\":\"https://mal.test/v2/whatever\"}}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("offset=100")))
                .andRespond(withSuccess("{\"data\":[{\"node\":{\"id\":2}}]}", MediaType.APPLICATION_JSON));

        List<Map<String, Object>> rows = client.fetchAnimeList(freshAccount());

        assertThat(rows).hasSize(2);
        server.verify();
    }

    /**
     * A month-long token will be at its end by some re-import — the situation AniList's
     * year-long tokens never taught this codebase to handle. It is refreshed before use,
     * and the new pair is written down: a refresh not persisted is a token spent for
     * nothing.
     */
    @Test
    void aTokenAtItsEndIsRefreshedAndTheNewPairPersisted() {
        when(oauth.refresh("refresh-token"))
                .thenReturn(new MalOAuthService.Tokens("new-token", "new-refresh", Instant.now().plusSeconds(2_678_400)));

        server.expect(requestTo(Matchers.startsWith(BASE + "/users/@me/animelist")))
                .andExpect(header("Authorization", "Bearer new-token"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        client.fetchAnimeList(account(Instant.now().minusSeconds(60)));

        verify(accounts).connect(eq(7L), eq(Provider.MAL), eq("reader"), eq("new-token"), eq("new-refresh"), any());
        server.verify();
    }

    /** An expired token with no refresh token has only one mend: the reader reconnecting. */
    @Test
    void anExpiredTokenWithoutARefreshTokenAsksForReconnection() {
        ExternalAccount dead = account(Instant.now().minusSeconds(60));
        dead.setRefreshToken(null);

        assertThatExceptionOfType(MalReconnectRequiredException.class)
                .isThrownBy(() -> client.fetchAnimeList(dead))
                .satisfies(e -> assertThat(e.advice()).contains("Reconnect"));
    }

    /** A token MAL rejects mid-run was revoked; the same token cannot fare better retried. */
    @Test
    void aRejectedTokenAsksForReconnectionRatherThanRetrying(){
        server.expect(ExpectedCount.once(), requestTo(Matchers.startsWith(BASE + "/users/@me/animelist")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatExceptionOfType(MalReconnectRequiredException.class)
                .isThrownBy(() -> client.fetchAnimeList(freshAccount()));

        server.verify();
    }

    /** The lesson AniList taught: one gateway blip must not lose a whole import. */
    @Test
    void aGatewayErrorIsRetriedBeforeItIsReported() {
        server.expect(ExpectedCount.times(3), requestTo(Matchers.startsWith(BASE + "/users/@me/animelist")))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatExceptionOfType(MalUnavailableException.class)
                .isThrownBy(() -> client.fetchAnimeList(freshAccount()))
                .satisfies(e -> assertThat(e.serviceName()).isEqualTo("MyAnimeList"));

        server.verify();
    }
}
