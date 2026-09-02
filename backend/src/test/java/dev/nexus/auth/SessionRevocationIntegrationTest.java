package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * What a signed JWT could not do on its own: stop working before it expires.
 *
 * <p>Every case here failed before there was a table of the tokens still allowed — the
 * token kept its full thirty days whatever the client or the account did with it.
 */
class SessionRevocationIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @Autowired
    RefreshTokenService sessions;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void signingOutStopsTheRefreshTokenWorking() {
        Response registered = register("player@example.com", "player");
        String cookie = registered.refreshCookiePair();

        assertThat(http.post("/auth/logout", "Cookie", cookie).status()).isEqualTo(204);

        assertThat(http.post("/auth/refresh", "Cookie", cookie).status()).isEqualTo(401);
    }

    @Test
    void refreshingRetiresTheTokenItReplaces() {
        Response registered = register("player@example.com", "player");
        String original = registered.refreshCookiePair();

        Response refreshed = http.post("/auth/refresh", "Cookie", original);
        assertThat(refreshed.status()).isEqualTo(200);

        assertThat(http.post("/auth/refresh", "Cookie", original).status()).isEqualTo(401);
        assertThat(http.post("/auth/refresh", "Cookie", refreshed.refreshCookiePair())
                        .status())
                .isEqualTo(200);
    }

    /** The case the whole table exists for: a session that cannot be reached to be signed out. */
    @Test
    void signingOutEverywhereEndsSessionsThisClientNeverSaw() {
        register("player@example.com", "player");
        Response phone = login("player@example.com", AuthClient.NATIVE);
        Response browser = login("player@example.com", AuthClient.WEB);

        Response signedOut = http.post("/auth/logout-all", "Authorization", "Bearer " + browser.accessToken());
        assertThat(signedOut.status()).isEqualTo(204);

        assertThat(refreshOf(phone).status()).isEqualTo(401);
        assertThat(http.post("/auth/refresh", "Cookie", browser.refreshCookiePair())
                        .status())
                .isEqualTo(401);
    }

    @Test
    void signingOutEverywhereNeedsAValidAccessToken() {
        assertThat(http.post("/auth/logout-all").status()).isEqualTo(401);
    }

    @Test
    void aNativeClientIsGivenItsTokenAndNoCookie() {
        register("player@example.com", "player");

        Response phone = login("player@example.com", AuthClient.NATIVE);

        assertThat(phone.body().get("refreshToken")).isNotNull();
        assertThat(phone.refreshCookie()).isEmpty();
    }

    @Test
    void aBrowserIsNeverToldItsOwnRefreshToken() {
        register("player@example.com", "player");

        Response browser = login("player@example.com", AuthClient.WEB);

        assertThat(browser.body()).doesNotContainKey("refreshToken");
        assertThat(browser.refreshCookie()).isPresent();
    }

    @Test
    void aNativeClientRefreshesWithTheTokenInTheBody() {
        register("player@example.com", "player");
        Response phone = login("player@example.com", AuthClient.NATIVE);

        Response refreshed = refreshOf(phone);

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.accessToken()).isNotBlank();
        // Renewing does not turn a phone's session into a browser's, so still no cookie.
        assertThat(refreshed.body().get("refreshToken")).isNotNull();
        assertThat(refreshed.refreshCookie()).isEmpty();
    }

    @Test
    void signingOutFromAPhoneEndsThatSessionToo() {
        register("player@example.com", "player");
        Response phone = login("player@example.com", AuthClient.NATIVE);

        http.postJson("/auth/logout", Map.of("refreshToken", refreshTokenOf(phone)));

        assertThat(refreshOf(phone).status()).isEqualTo(401);
    }

    @Test
    void theSweepDropsRowsWhoseTokensHaveExpired() {
        register("player@example.com", "player");
        refreshTokens.save(new RefreshToken(
                UUID.randomUUID(), liveUserId(), AuthClient.NATIVE, Instant.now().minus(1, ChronoUnit.DAYS)));
        long before = refreshTokens.count();

        sessions.pruneExpired();

        assertThat(refreshTokens.count()).isLessThan(before);
        // The live session the account still has is untouched.
        assertThat(refreshTokens.count()).isEqualTo(1);
    }

    private Long liveUserId() {
        return refreshTokens.findAll().getFirst().getUserId();
    }

    private String refreshTokenOf(Response response) {
        return (String) response.body().get("refreshToken");
    }

    private Response refreshOf(Response session) {
        return http.postJson("/auth/refresh", Map.of("refreshToken", refreshTokenOf(session)));
    }

    private Response register(String email, String username) {
        return http.postJson(
                "/auth/register", Map.of("email", email, "username", username, "password", PASSWORD));
    }

    private Response login(String email, AuthClient client) {
        return http.postJson("/auth/login", Map.of("email", email, "password", PASSWORD, "client", client.name()));
    }
}
