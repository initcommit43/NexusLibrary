package dev.nexus.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * TLS ends at Railway's edge and reaches the app as plain http, so {@code request.isSecure()}
 * is false in production too. Spring's own HSTS writer waits for a secure request, which is how
 * the header went missing unnoticed. These requests are plain http on purpose: that is the
 * case the setting has to cover.
 */
@TestPropertySource(properties = "nexus.security.cookie-secure=true")
class HstsIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void aPlainHttpRequestGetsAYearOfHstsCoveringSubdomains() {
        String hsts = http.get("/health").header("Strict-Transport-Security").orElse("");

        assertThat(hsts).contains("max-age=31536000").contains("includeSubDomains");
    }

    /** The first visit is often signed out, and that answer has to carry it as well. */
    @Test
    void aRefusedRequestCarriesItToo() {
        HttpTestClient.Response refused = http.get("/entries");

        assertThat(refused.status()).isEqualTo(401);
        assertThat(refused.header("Strict-Transport-Security")).isPresent();
    }

    /** Preload is a submission to a public list and takes months to undo, so it stays off. */
    @Test
    void itIsNeverPreloaded() {
        assertThat(http.get("/health").header("Strict-Transport-Security").orElse(""))
                .isNotEmpty()
                .doesNotContain("preload");
    }
}
