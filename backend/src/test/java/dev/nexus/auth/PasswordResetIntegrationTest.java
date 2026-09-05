package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Getting back in without the old password, and the four things that keep that from being a
 * way in for anyone else: the link is unguessable, single use, short-lived, and says nothing
 * about who has an account here.
 */
class PasswordResetIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String NEW_PASSWORD = "an-entirely-new-secret";

    @LocalServerPort
    int port;

    /** Stands in for the sender, and is how the test reads the link the reader would be mailed. */
    @MockitoBean
    PasswordResetMailer mailer;

    @Autowired
    PasswordResetTokenRepository resetTokens;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void aMailedLinkSetsTheNewPasswordAndTheOldOneStopsWorking() {
        register("player@example.com", "player");

        assertThat(requestLink("player@example.com").status()).isEqualTo(204);
        assertThat(reset(mailedToken(), NEW_PASSWORD).status()).isEqualTo(204);

        assertThat(login("player@example.com", NEW_PASSWORD).status()).isEqualTo(200);
        assertThat(login("player@example.com", PASSWORD).status()).isEqualTo(401);
    }

    /**
     * The whole point of answering identically: this endpoint is public and takes an address,
     * so a different answer for a registered one would be a way to ask who has an account.
     */
    @Test
    void anAddressWithNoAccountIsAnsweredExactlyLikeOneThatHasOne() {
        register("player@example.com", "player");

        Response known = requestLink("player@example.com");
        Response stranger = requestLink("nobody@example.com");

        assertThat(stranger.status()).isEqualTo(known.status()).isEqualTo(204);
        assertThat(stranger.rawBody()).isEqualTo(known.rawBody());

        // Only the account that exists was mailed anything, and only it has a link waiting.
        verify(mailer, times(1)).send(any(), any(), any());
        assertThat(resetTokens.count()).isEqualTo(1);
    }

    @Test
    void aLinkWorksOnceAndIsDeadAfterwards() {
        register("player@example.com", "player");
        requestLink("player@example.com");
        String token = mailedToken();

        assertThat(reset(token, NEW_PASSWORD).status()).isEqualTo(204);

        assertThat(reset(token, "a-third-password-entirely").status()).isEqualTo(400);
        assertThat(login("player@example.com", "a-third-password-entirely").status())
                .isEqualTo(401);
    }

    /** Thirty minutes, and the row's own expiry is what enforces it rather than a sweep. */
    @Test
    void aLinkPastItsHalfHourIsRefused() {
        register("player@example.com", "player");
        requestLink("player@example.com");
        String token = mailedToken();

        jdbc.update("UPDATE password_reset_token SET expires_at = now() - interval '1 minute'");

        assertThat(reset(token, NEW_PASSWORD).status()).isEqualTo(400);
        assertThat(login("player@example.com", PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void theLinkIsIssuedForThirtyMinutes() {
        register("player@example.com", "player");
        requestLink("player@example.com");

        verify(mailer).send(any(), any(), eq(Duration.ofMinutes(30)));
    }

    /** Asking again retires the last link, so a resent or forwarded older mail is already dead. */
    @Test
    void onlyTheNewestLinkWorks() {
        register("player@example.com", "player");
        requestLink("player@example.com");
        requestLink("player@example.com");

        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(mailer, times(2)).send(any(), links.capture(), any());

        assertThat(reset(tokenIn(links.getAllValues().get(0)), NEW_PASSWORD).status())
                .isEqualTo(400);
        assertThat(reset(tokenIn(links.getAllValues().get(1)), NEW_PASSWORD).status())
                .isEqualTo(204);
    }

    /**
     * Most people reset because they have lost control of the account or of a device holding
     * it. Leaving the sessions the old password opened would hand it straight back.
     */
    @Test
    void resettingEndsEverySessionTheAccountHad() {
        Response registered = register("player@example.com", "player");
        String cookie = registered.refreshCookiePair();

        requestLink("player@example.com");
        reset(mailedToken(), NEW_PASSWORD);

        assertThat(http.post("/auth/refresh", "Cookie", cookie).status()).isEqualTo(401);
    }

    /** Proving you can read a mailbox is not proving you know the password just set. */
    @Test
    void resettingHandsBackNoSessionOfItsOwn() {
        register("player@example.com", "player");
        requestLink("player@example.com");

        Response reset = reset(mailedToken(), NEW_PASSWORD);

        assertThat(reset.setCookie()).isEmpty();
        assertThat(reset.rawBody()).isEmpty();
    }

    /**
     * A 400 rather than a 401. Nobody is signed in on the reset page, and the client reads a
     * 401 as its own session ending — it would report a lost session instead of a dead link.
     */
    @Test
    void anUnknownTokenIsRefusedWithoutBeingTreatedAsALostSession() {
        Response response = reset("a-token-that-was-never-issued", NEW_PASSWORD);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("message").toString()).doesNotContain("Exception");
    }

    @Test
    void aResetIsHeldToTheSamePasswordRulesAsRegistration() {
        register("player@example.com", "player");
        requestLink("player@example.com");

        Response response = reset(mailedToken(), "short");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("password");
    }

    @Test
    void bothRoutesAreReachableWithoutBeingSignedIn() {
        assertThat(requestLink("nobody@example.com").status()).isEqualTo(204);
        assertThat(reset("a-token-that-was-never-issued", NEW_PASSWORD).status())
                .isEqualTo(400);
    }

    /** The row is a digest, not the credential: reading this table must not be a way in. */
    @Test
    void theLinkItselfIsNeverStored() {
        register("player@example.com", "player");
        requestLink("player@example.com");
        String token = mailedToken();

        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset_token", String.class);

        assertThat(stored).isNotNull().hasSize(64).isNotEqualTo(token).doesNotContain(token);
    }

    @Test
    void anAddressThatIsNotOneIsRejectedAtTheEdge() {
        Response response = http.postJson("/auth/forgot-password", Map.of("email", "not-an-email"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("email");
    }

    /** Case is not what tells two accounts apart, here or at the sign-in this has to agree with. */
    @Test
    void theAddressIsMatchedTheWaySigningInMatchesIt() {
        register("player@example.com", "player");

        assertThat(requestLink("PLAYER@Example.COM").status()).isEqualTo(204);

        assertThat(reset(mailedToken(), NEW_PASSWORD).status()).isEqualTo(204);
        assertThat(login("player@example.com", NEW_PASSWORD).status()).isEqualTo(200);
    }

    /** The link the mailer was last handed, as the reader's browser would present it. */
    private String mailedToken() {
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mailer, atLeastOnce()).send(any(), link.capture(), any());

        return tokenIn(link.getValue());
    }

    private String tokenIn(String link) {
        assertThat(link).contains("/reset-password?token=");
        return link.substring(link.indexOf("token=") + "token=".length());
    }

    private Response requestLink(String email) {
        return http.postJson("/auth/forgot-password", Map.of("email", email));
    }

    private Response reset(String token, String password) {
        return http.postJson("/auth/reset-password", Map.of("token", token, "password", password));
    }

    private Response register(String email, String username) {
        return http.postJson(
                "/auth/register",
                Map.of("email", email, "username", username, "password", PASSWORD, "client", "WEB"));
    }

    private Response login(String email, String password) {
        return http.postJson("/auth/login", Map.of("email", email, "password", password, "client", "WEB"));
    }
}
