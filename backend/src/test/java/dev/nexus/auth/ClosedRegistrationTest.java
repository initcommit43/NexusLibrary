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
 * A deployment that has stopped taking accounts.
 *
 * <p>Its URL is public — anyone who finds it can reach the sign-up form — and an account made
 * there spends the API budget of whoever set it up. Signing in is untouched: the people who
 * already have accounts are the point of the deployment.
 */
@TestPropertySource(properties = "nexus.security.registration-open=false")
class ClosedRegistrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void nobodyNewCanSignUp() {
        Response refused = http.postJson(
                "/auth/register",
                Map.of("email", "stranger@example.com", "username", "stranger", "password", PASSWORD, "client", "WEB"));

        assertThat(refused.status()).isEqualTo(403);
        assertThat(String.valueOf(refused.body().get("message"))).contains("not taking new accounts");
    }

    /** Said plainly rather than by pretending the route is not there. */
    @Test
    void theRefusalIsNotAMissingPage() {
        assertThat(http.postJson("/auth/register", Map.of()).status()).isNotEqualTo(404);
    }
}
