package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.security.SiteGate;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** The site password in front of everything, with the gate switched on for this context only. */
@TestPropertySource(
        properties = {
            "nexus.site-gate.password=" + SiteGateIntegrationTest.PASSWORD
        })
class SiteGateIntegrationTest extends PostgresIntegrationTest {

    static final String PASSWORD = "open-sesame-for-tests";

    @LocalServerPort
    int port;

    @Autowired
    SiteGate gate;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    @Test
    void aPageAndTheAppShellAnswerWithThePasswordPage() {
        for (String path : new String[] {"/", "/library/anime/anime"}) {
            Response page = http.getRoot(path);

            assertThat(page.status()).isEqualTo(401);
            assertThat(page.header("Content-Type").orElse("")).startsWith("text/html");
            assertThat(page.rawBody()).contains("name=\"password\"").contains("NexusLibrary");
            assertThat(page.header("X-Content-Type-Options")).contains("nosniff");
            assertThat(page.header("X-Frame-Options")).contains("DENY");
        }
    }

    @Test
    void thePasswordPageRemembersWhereTheBrowserWasGoing() {
        assertThat(http.getRoot("/library/anime/anime?view=list").rawBody())
                .contains("value=\"/library/anime/anime?view=list\"");
    }

    @Test
    void theApiAnswersLockedJson() {
        Response api = http.get("/config");

        assertThat(api.status()).isEqualTo(401);
        assertThat(api.body()).containsEntry("message", "This site is locked.");
    }

    @Test
    void theCookieOpensTheApiAndThePages() {
        String cookie = "nexus_site=" + gate.mint(Instant.now());

        assertThat(http.get("/config", "Cookie", cookie).status()).isEqualTo(200);
        Response page = http.getRoot("/library/anime/anime", "Cookie", cookie);
        assertThat(page.status()).isNotEqualTo(401);
        assertThat(page.rawBody()).doesNotContain("name=\"password\"");
    }

    @Test
    void aTamperedCookieIsRefused() {
        String cookie = "nexus_site=" + gate.mint(Instant.now());
        String tampered = cookie.substring(0, cookie.length() - 1) + (cookie.endsWith("A") ? "B" : "A");

        assertThat(http.get("/config", "Cookie", tampered).status()).isEqualTo(401);
    }

    @Test
    void anExpiredCookieIsRefused() {
        String expired = gate.mint(Instant.now().minus(SiteGate.LIFETIME).minus(Duration.ofMinutes(1)));

        assertThat(http.get("/config", "Cookie", "nexus_site=" + expired).status()).isEqualTo(401);
    }

    @Test
    void theHealthCheckStaysOpen() {
        assertThat(http.get("/health").status()).isEqualTo(200);
    }
}
