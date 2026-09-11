package dev.nexus.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.AuthenticatedTest;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * What the security chain adds to every answer, and what it refuses by default. Nothing else
 * in the suite fails if a header quietly stops being written or a route quietly opens.
 */
class SecurityHeadersIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    /** A reader's own data must not be kept by the browser or by anything between it and us. */
    @Test
    void anAuthenticatedAnswerIsNotStored() {
        String token = AuthenticatedTest.registerAndGetToken(http, "player@example.com", "player");

        Response me = http.get("/auth/me", "Authorization", "Bearer " + token);

        assertThat(me.status()).isEqualTo(200);
        assertThat(me.header("Cache-Control").orElse("")).contains("no-store");
        assertThat(me.header("Pragma")).contains("no-cache");
    }

    @Test
    void answersForbidSniffingAndFraming() {
        Response health = http.get("/health");

        assertThat(health.header("X-Content-Type-Options")).contains("nosniff");
        assertThat(health.header("X-Frame-Options")).contains("DENY");
    }

    /** The test profile has cookie-secure off, as local dev over plain http does. */
    @Test
    void noHstsIsSentWhereTheDeploymentIsNotBehindTls() {
        assertThat(http.get("/health").header("Strict-Transport-Security")).isEmpty();
    }

    /**
     * A route nobody has written yet is closed as well, so a new controller cannot become
     * public by being left off a list.
     */
    @Test
    void anUnlistedApiPathNeedsATokenEvenWhenNothingServesIt() {
        assertThat(http.get("/no-such-route").status()).isEqualTo(401);
        assertThat(http.postJson("/no-such-route", Map.of()).status()).isEqualTo(401);
    }

    /** The whitelist names exact paths, so an entry opens nothing beneath it. */
    @Test
    void aWhitelistedPathOpensNothingBeneathIt() {
        assertThat(http.get("/health/details").status()).isEqualTo(401);
        assertThat(http.postJson("/auth/login/anything", Map.of()).status()).isEqualTo(401);
    }

    @Test
    void whitelistedRoutesAnswerWithoutAToken() {
        assertThat(http.get("/health").status()).isEqualTo(200);
        assertThat(http.get("/config").status()).isEqualTo(200);
        assertThat(http.get("/client-version").status()).isEqualTo(200);
    }
}
