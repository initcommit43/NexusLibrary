package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.security.SiteGate;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/** The site password in front of everything, with the gate switched on for this context only. */
@TestPropertySource(
        properties = {
            "nexus.site-gate.password=" + SiteGateIntegrationTest.PASSWORD,
            "nexus.rate-limit.auth-requests-per-minute=" + SiteGateIntegrationTest.ATTEMPTS,
            // So the rate-limit test can take a bucket of its own through X-Forwarded-For.
            "nexus.security.trusted-proxy-count=1"
        })
class SiteGateIntegrationTest extends PostgresIntegrationTest {

    static final String PASSWORD = "open-sesame-for-tests";
    static final int ATTEMPTS = 50;

    @LocalServerPort
    int port;

    @Autowired
    SiteGate gate;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        http = new HttpTestClient(port);
    }

    private Response unlock(String password, String next) {
        return http.postFormRoot("/site-gate", Map.of("password", password, "next", next));
    }

    private static String cookiePair(Response response) {
        return response.setCookie().stream()
                .filter(cookie -> cookie.startsWith("nexus_site="))
                .findFirst()
                .orElseThrow()
                .split(";", 2)[0];
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
    void theWrongPasswordSetsNoCookie() {
        Response wrong = unlock("not-it", "/");

        assertThat(wrong.status()).isEqualTo(401);
        assertThat(wrong.rawBody()).contains("That password is not right.");
        assertThat(wrong.setCookie()).noneMatch(cookie -> cookie.startsWith("nexus_site="));
    }

    @Test
    void theRightPasswordSetsTheCookieAndReturnsToThePage() {
        Response right = unlock(PASSWORD, "/settings");

        assertThat(right.status()).isEqualTo(303);
        assertThat(right.header("Location")).contains("/settings");
        String cookie = right.setCookie().stream()
                .filter(value -> value.startsWith("nexus_site="))
                .findFirst()
                .orElseThrow();
        assertThat(cookie).contains("HttpOnly").contains("Path=/").contains("SameSite=Lax");
    }

    @Test
    void aRedirectOffTheSiteGoesToTheStartInstead() {
        for (String next : new String[] {"//evil.example.com", "https://evil.example.com", "/\\evil.example.com"}) {
            assertThat(unlock(PASSWORD, next).header("Location").orElseThrow()).isEqualTo("/");
        }
    }

    @Test
    void theCookieOpensTheApiAndThePages() {
        String cookie = cookiePair(unlock(PASSWORD, "/"));

        assertThat(http.get("/config", "Cookie", cookie).status()).isEqualTo(200);
        Response page = http.getRoot("/library/anime/anime", "Cookie", cookie);
        assertThat(page.status()).isNotEqualTo(401);
        assertThat(page.rawBody()).doesNotContain("name=\"password\"");
    }

    @Test
    void aTamperedCookieIsRefused() {
        String cookie = cookiePair(unlock(PASSWORD, "/"));
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

    @Test
    void guessingIsThrottled() {
        String caller = "203.0.113.77";
        int lastStatus = 0;
        for (int attempt = 0; attempt <= ATTEMPTS; attempt++) {
            lastStatus = http.postFormRoot(
                            "/site-gate", Map.of("password", "guess-" + attempt, "next", "/"), "X-Forwarded-For", caller)
                    .status();
        }

        assertThat(lastStatus).isEqualTo(429);
    }
}
