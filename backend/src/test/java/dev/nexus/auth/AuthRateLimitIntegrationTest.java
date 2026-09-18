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
 * {@code /auth/refresh} and {@code /auth/logout} are public, and each call parses a signed
 * token and then reads its row. Forging one is not the threat a limit defends against here —
 * asking for that work for free is. So the limit has to bite on a token that was never valid,
 * which is what these send.
 */
@TestPropertySource(
        properties = {
            "nexus.rate-limit.auth-requests-per-minute=" + AuthRateLimitIntegrationTest.ATTEMPTS,
            // So each test takes a bucket of its own through X-Forwarded-For.
            "nexus.security.trusted-proxy-count=1"
        })
class AuthRateLimitIntegrationTest extends PostgresIntegrationTest {

    static final int ATTEMPTS = 5;

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void refreshingTooOftenIsThrottled() {
        int lastStatus = 0;
        for (int attempt = 0; attempt <= ATTEMPTS; attempt++) {
            lastStatus = spend("/auth/refresh", "203.0.113.10").status();
        }

        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    void loggingOutTooOftenIsThrottled() {
        int lastStatus = 0;
        for (int attempt = 0; attempt <= ATTEMPTS; attempt++) {
            lastStatus = spend("/auth/logout", "203.0.113.11").status();
        }

        assertThat(lastStatus).isEqualTo(429);
    }

    /** The two endpoints do not share a bucket: spending one's allowance leaves the other's. */
    @Test
    void refreshAndLogoutAreCountedSeparately() {
        String caller = "203.0.113.12";
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            spend("/auth/refresh", caller);
        }

        assertThat(spend("/auth/logout", caller).status()).isNotEqualTo(429);
    }

    private Response spend(String path, String caller) {
        return http.postJson(path, Map.of("refreshToken", "not-a-real-token"), "X-Forwarded-For", caller);
    }
}
