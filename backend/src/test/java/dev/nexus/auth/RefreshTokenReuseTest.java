package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * What happens when a retired refresh token turns up too late to be a race.
 *
 * <p>The grace window is zero here so the case can be reached without the test sleeping
 * through the real one. {@link SessionRevocationIntegrationTest} covers the other side of the
 * same branch, where the window has not passed and a racing client is simply refused.
 */
@TestPropertySource(properties = "nexus.jwt.refresh-reuse-grace-seconds=0")
class RefreshTokenReuseTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    /**
     * Rotation alone cannot tell a thief from the owner: whichever of them refreshes first
     * holds the working token, and the other is the one locked out. A retired token presented
     * past the window says the pair exists, and the only safe reading is that neither is
     * trusted — so every session goes, including the one that just refreshed successfully.
     */
    @Test
    void aTokenPresentedAfterItsWindowEndsEverySession() {
        register("player@example.com", "player");
        Response browser = login("player@example.com", AuthClient.WEB);
        Response phone = login("player@example.com", AuthClient.NATIVE);

        String stolen = browser.refreshCookiePair();
        Response refreshed = http.post("/auth/refresh", "Cookie", stolen);
        assertThat(refreshed.status()).isEqualTo(200);

        assertThat(http.post("/auth/refresh", "Cookie", stolen).status()).isEqualTo(401);

        assertThat(http.post("/auth/refresh", "Cookie", refreshed.refreshCookiePair())
                        .status())
                .isEqualTo(401);
        assertThat(http.postJson("/auth/refresh", Map.of("refreshToken", (String) phone.body().get("refreshToken")))
                        .status())
                .isEqualTo(401);
    }

    /** A token that simply ran out is nobody's fault, and ends nothing but itself. */
    @Test
    void anExpiredTokenIsNotTreatedAsTheft() {
        register("player@example.com", "player");
        Response phone = login("player@example.com", AuthClient.NATIVE);

        assertThat(http.postJson("/auth/refresh", Map.of("refreshToken", "not-a-token"))
                        .status())
                .isEqualTo(401);

        assertThat(http.postJson("/auth/refresh", Map.of("refreshToken", (String) phone.body().get("refreshToken")))
                        .status())
                .isEqualTo(200);
    }

    private Response register(String email, String username) {
        return http.postJson(
                "/auth/register",
                Map.of("email", email, "username", username, "password", PASSWORD, "client", "WEB", "dateOfBirth", "1990-01-01", "acceptedTerms", true));
    }

    private Response login(String email, AuthClient client) {
        return http.postJson("/auth/login", Map.of("email", email, "password", PASSWORD, "client", client.name()));
    }
}
