package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/** With no site password configured, which is every local run, nothing is gated. */
class SiteGateOffIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void nothingIsLockedWithoutAPassword() {
        HttpTestClient http = new HttpTestClient(port);

        assertThat(http.get("/config").status()).isEqualTo(200);
        assertThat(http.getRoot("/library/anime/anime").rawBody()).doesNotContain("name=\"password\"");
    }
}
