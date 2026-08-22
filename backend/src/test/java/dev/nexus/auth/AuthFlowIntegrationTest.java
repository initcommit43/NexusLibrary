package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

class AuthFlowIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        http = new HttpTestClient(port);
    }

    @Test
    void registerReturnsATokenAndSetsAnHttpOnlyRefreshCookie() {
        Response response = register("player@example.com", "player", PASSWORD);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshCookie()).isPresent();
        assertThat(response.refreshCookie().orElseThrow())
                .contains("HttpOnly")
                .contains("SameSite=Strict");
    }

    @Test
    void theAccessTokenIsNeverPutInACookie() {
        Response response = register("player@example.com", "player", PASSWORD);

        assertThat(response.setCookie()).noneMatch(cookie -> cookie.contains(response.accessToken()));
    }

    @Test
    void registerRejectsADuplicateEmailWithAFieldError() {
        register("player@example.com", "player", PASSWORD);

        Response duplicate = register("player@example.com", "other", PASSWORD);

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.fieldErrors()).containsKey("email");
    }

    @Test
    void registerRejectsADuplicateUsername() {
        register("first@example.com", "player", PASSWORD);

        Response duplicate = register("second@example.com", "player", PASSWORD);

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.fieldErrors()).containsKey("username");
    }

    @Test
    void registerRejectsAShortPassword() {
        Response response = register("player@example.com", "player", "short");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("password");
    }

    @Test
    void registerRejectsAMalformedEmail() {
        Response response = register("not-an-email", "player", PASSWORD);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("email");
    }

    @Test
    void loginSucceedsWithTheRightPasswordAndFailsWithTheWrongOne() {
        register("player@example.com", "player", PASSWORD);

        assertThat(login("player@example.com", PASSWORD).status()).isEqualTo(200);
        assertThat(login("player@example.com", "wrong-password-entirely").status())
                .isEqualTo(401);
    }

    @Test
    void loginIsCaseInsensitiveOnEmail() {
        register("player@example.com", "player", PASSWORD);

        assertThat(login("PLAYER@Example.COM", PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void meRequiresAValidAccessToken() {
        String token = register("player@example.com", "player", PASSWORD).accessToken();

        assertThat(authedGet("/auth/me", token).status()).isEqualTo(200);
        assertThat(http.get("/auth/me").status()).isEqualTo(401);
        assertThat(authedGet("/auth/me", "forged.token.value").status()).isEqualTo(401);
    }

    @Test
    void refreshCookieExchangesForANewAccessToken() {
        Response registered = register("player@example.com", "player", PASSWORD);

        Response refreshed = http.post("/auth/refresh", "Cookie", registered.refreshCookiePair());

        assertThat(refreshed.status()).isEqualTo(200);
        assertThat(refreshed.accessToken()).isNotBlank();
    }

    @Test
    void refreshWithoutACookieIsRejected() {
        assertThat(http.post("/auth/refresh").status()).isEqualTo(401);
    }

    @Test
    void refreshWithAForgedCookieIsRejected() {
        assertThat(http.post("/auth/refresh", "Cookie", "nexus_refresh=forged.token.value")
                        .status())
                .isEqualTo(401);
    }

    @Test
    void theRefreshTokenCannotBeUsedAsAnAccessToken() {
        Response registered = register("player@example.com", "player", PASSWORD);
        String refreshToken = registered.refreshCookiePair().split("=", 2)[1];

        assertThat(authedGet("/auth/me", refreshToken).status()).isEqualTo(401);
    }

    @Test
    void logoutClearsTheRefreshCookie() {
        Response logout = http.post("/auth/logout");

        assertThat(logout.status()).isEqualTo(204);
        assertThat(logout.refreshCookie().orElseThrow()).contains("Max-Age=0");
    }

    @Test
    void eachTokenOnlyEverResolvesToItsOwnAccount() {
        register("first@example.com", "first", PASSWORD);
        String secondToken = register("second@example.com", "second", PASSWORD).accessToken();

        Response me = authedGet("/auth/me", secondToken);

        assertThat(me.body().get("email")).isEqualTo("second@example.com");
    }

    @Test
    void responsesNeverCarryThePasswordHash() {
        Response registered = register("player@example.com", "player", PASSWORD);
        String me = authedGet("/auth/me", registered.accessToken()).body().toString();

        assertThat(me).doesNotContain("passwordHash").doesNotContain("$2a$");
    }

    @Test
    void errorResponsesDoNotLeakInternals() {
        Response failed = login("nobody@example.com", PASSWORD);

        assertThat(failed.body()).doesNotContainKeys("trace", "exception", "path");
        assertThat(failed.body().get("message").toString()).doesNotContain("Exception");
    }

    @Test
    void healthIsPublic() {
        assertThat(http.get("/health").status()).isEqualTo(200);
    }

    private Response register(String email, String username, String password) {
        return http.postJson(
                "/auth/register", Map.of("email", email, "username", username, "password", password));
    }

    private Response login(String email, String password) {
        return http.postJson("/auth/login", Map.of("email", email, "password", password));
    }

    private Response authedGet(String path, String token) {
        return http.get(path, "Authorization", "Bearer " + token);
    }
}
