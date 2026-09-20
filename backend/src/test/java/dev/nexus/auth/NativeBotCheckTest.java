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
 * What stands in front of sign-up once the Turnstile keys are set.
 *
 * <p>Cloudflare ships no native SDK, so the challenge is asked of browsers only and a native
 * sign-up pays for its exemption with a confirmed address instead. The suite normally runs with
 * no secret key, which switches the challenge off entirely — so without this class the branch
 * that decides between the two would never be exercised at all.
 *
 * <p>Nothing here reaches Cloudflare: a missing token is refused before the outbound call, and
 * the native path never makes one.
 */
@TestPropertySource(
        properties = {
            "nexus.turnstile.secret-key=test-secret-not-a-real-key",
            // Off deployment-wide on purpose. The native path has to close the gate by itself,
            // or the exemption rests on a setting someone can flip.
            "nexus.verification.required=false"
        })
class NativeBotCheckTest extends PostgresIntegrationTest {

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
    void aBrowserWithNoTokenIsRefused() {
        Response refused = http.postJson("/auth/register", signUp("browser@example.com", "browser", "WEB"));

        assertThat(refused.status()).isEqualTo(400);
    }

    /** The whole point: the app can sign someone up without a token it cannot obtain. */
    @Test
    void aNativeClientNeedsNoToken() {
        Response accepted = http.postJson("/auth/register", signUp("phone@example.com", "phone", "NATIVE"));

        assertThat(accepted.status()).isEqualTo(202);
    }

    /**
     * What the exemption costs. {@code client} is caller-chosen, so this is also what anyone
     * claiming NATIVE to skip the challenge gets: an account that opens nothing until a mailbox
     * they own is read, whatever the deployment-wide setting says.
     */
    @Test
    void aNativeSignUpIsHandedNoSessionEvenWhenVerificationIsOffDeploymentWide() {
        Response accepted = http.postJson("/auth/register", signUp("unopened@example.com", "unopened", "NATIVE"));

        assertThat(accepted.status()).isEqualTo(202);
        assertThat(accepted.body()).doesNotContainKey("accessToken");
        assertThat(accepted.refreshCookie()).isEmpty();
    }

    /**
     * The same exemption on the reset flow, which the app needs for an in-app "forgot password?"
     * — throwing someone out to a browser mid-sign-in is not a flow worth shipping. Nothing
     * here is created to charge for it, so what pays is the per-account cap in
     * {@link PasswordResetService}; see {@link PasswordResetIntegrationTest} for that half.
     */
    @Test
    void aNativeClientAskingForAResetNeedsNoToken() {
        http.postJson("/auth/register", signUp("locked@example.com", "locked", "NATIVE"));

        Response asked = http.postJson(
                "/auth/forgot-password", Map.of("email", "locked@example.com", "client", "NATIVE"));

        assertThat(asked.status()).isEqualTo(204);
    }

    @Test
    void aBrowserAskingForAResetWithNoTokenIsRefused() {
        Response refused = http.postJson("/auth/forgot-password", Map.of("email", "locked@example.com"));

        assertThat(refused.status()).isEqualTo(400);
    }

    /** A token the app sends anyway is not a reason to refuse it. */
    @Test
    void aNativeClientThatSendsATokenIsNotPunishedForIt() {
        Map<String, Object> body = new java.util.HashMap<>(signUp("polite@example.com", "polite", "NATIVE"));
        body.put("turnstileToken", "whatever-the-app-happened-to-send");

        assertThat(http.postJson("/auth/register", body).status()).isEqualTo(202);
    }

    private static Map<String, Object> signUp(String email, String username, String client) {
        return Map.of(
                "email", email,
                "username", username,
                "password", PASSWORD,
                "client", client,
                "dateOfBirth", "1990-01-01",
                "acceptedTerms", true);
    }
}
