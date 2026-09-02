package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * The launch ping that lets a shipped app be retired.
 *
 * <p>The point of the test is that it answers without a token: a build old enough to be
 * refused is one that cannot be trusted to sign in first.
 */
@TestPropertySource(properties = "nexus.client-version.minimum=1.4.0")
class ClientVersionIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void tellsAnAppTheOldestBuildStillServed() {
        Response answered = http.get("/client-version");

        assertThat(answered.status()).isEqualTo(200);
        assertThat(answered.body().get("minimumVersion")).isEqualTo("1.4.0");
    }
}
