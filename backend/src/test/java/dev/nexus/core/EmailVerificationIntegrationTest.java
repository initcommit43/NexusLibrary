package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.mail.Mailer;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The gate, closed — which is the configuration a deployment with a verified sending domain
 * runs, and the one worth testing, since it is the one that can lock someone out.
 */
@TestPropertySource(properties = "nexus.verification.required=true")
class EmailVerificationIntegrationTest extends PostgresIntegrationTest {

    private static final Pattern LINK = Pattern.compile("/verify-email\\?token=([A-Za-z0-9_-]+)");

    @LocalServerPort
    int port;

    /** Stands in for the sender so the link can be read without anything leaving the machine. */
    @MockitoBean
    Mailer mailer;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void registeringHandsBackNoSession() {
        Response registered = register("reader@example.com", "reader");

        assertThat(registered.status()).isEqualTo(202);
        assertThat(registered.body()).doesNotContainKey("accessToken");
        assertThat(registered.refreshCookie()).isEmpty();
    }

    @Test
    void registeringSendsALink() {
        register("reader@example.com", "reader");

        verify(mailer).send(eq("reader@example.com"), anyString(), anyString());
    }

    @Test
    void anUnverifiedAccountCannotSignIn() {
        register("reader@example.com", "reader");

        Response refused = signIn("reader@example.com");

        assertThat(refused.status()).isEqualTo(403);
        assertThat(refused.fieldErrors()).containsEntry("email", "unverified");
    }

    /** The whole point: follow the link, then the account opens. */
    @Test
    void followingTheLinkLetsTheAccountSignIn() {
        register("reader@example.com", "reader");

        assertThat(verifyWith(sentToken()).status()).isEqualTo(204);
        assertThat(users.findByEmail("reader@example.com").orElseThrow().isEmailVerified()).isTrue();
        assertThat(signIn("reader@example.com").status()).isEqualTo(200);
    }

    /** A link is one use. A second click, or a forwarded mail, opens nothing. */
    @Test
    void aSpentLinkIsRefused() {
        register("reader@example.com", "reader");
        String token = sentToken();
        verifyWith(token);

        assertThat(verifyWith(token).status()).isEqualTo(410);
    }

    @Test
    void anUnknownTokenIsRefused() {
        assertThat(verifyWith("not-a-real-token").status()).isEqualTo(410);
    }

    /**
     * A wrong password on an unverified account answers as a wrong password. Answering
     * "confirm your email" would tell whoever guessed that the address has an account here.
     */
    @Test
    void aWrongPasswordNeverMentionsVerification() {
        register("reader@example.com", "reader");

        Response refused = http.postJson(
                "/auth/login",
                Map.of("email", "reader@example.com", "password", "the-wrong-password", "client", "WEB"));

        assertThat(refused.status()).isEqualTo(401);
    }

    /** Says the same for an address with no account, so it cannot be used to ask who is here. */
    @Test
    void resendingSaysNothingAboutWhoExists() {
        Response forStranger =
                http.postJson("/auth/verify-email/resend", Map.of("email", "nobody@example.com"));

        assertThat(forStranger.status()).isEqualTo(202);
        verify(mailer, never()).send(eq("nobody@example.com"), anyString(), anyString());
    }

    @Test
    void anAlreadyVerifiedAddressIsSentNothing() {
        register("reader@example.com", "reader");
        verifyWith(sentToken());
        org.mockito.Mockito.clearInvocations(mailer);

        http.postJson("/auth/verify-email/resend", Map.of("email", "reader@example.com"));

        verify(mailer, never()).send(anyString(), anyString(), anyString());
    }

    /** Reads the token out of whatever was handed to the sender. */
    private String sentToken() {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mailer, org.mockito.Mockito.atLeastOnce()).send(anyString(), anyString(), body.capture());

        Matcher found = LINK.matcher(body.getValue());
        assertThat(found.find()).as("the mail carries a verification link").isTrue();
        return found.group(1);
    }

    private Response verifyWith(String token) {
        return http.postJson("/auth/verify-email", Map.of("token", token));
    }

    private Response signIn(String email) {
        return http.postJson(
                "/auth/login", Map.of("email", email, "password", "a-long-enough-password", "client", "WEB"));
    }

    private Response register(String email, String username) {
        return http.postJson(
                "/auth/register",
                Map.of(
                        "email", email,
                        "username", username,
                        "password", "a-long-enough-password",
                        "client", "WEB",
                        "dateOfBirth", "1990-01-01",
                        "acceptedTerms", true));
    }
}
