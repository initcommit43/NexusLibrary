package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.PASSWORD;
import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Renaming an account, changing how it is signed into, and the two rights the law gives it. */
class AccountIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");
        registerAndGetToken(http, "taken@example.com", "taken");
    }

    private String auth() {
        return "Bearer " + token;
    }

    private Response patch(Map<String, ?> body) {
        return http.patchJson("/settings/account", body, "Authorization", auth());
    }

    @Test
    void aReaderCanRenameThemselves() {
        Response response = patch(Map.of("username", "renamed"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).containsEntry("username", "renamed");
        assertThat(users.existsByUsernameIgnoreCase("renamed")).isTrue();
    }

    /** Case-folded exactly as registration folds it, or the two would disagree about who is taken. */
    @Test
    void anEmailIsStoredTheWayRegistrationStoresIt() {
        assertThat(patch(Map.of("email", "New.Address@Example.com")).body())
                .containsEntry("email", "new.address@example.com");
    }

    /** Validation reads the address before anything trims it, as it does at registration. */
    @Test
    void anAddressThatIsNotOneIsRefused() {
        assertThat(patch(Map.of("email", " padded@example.com ")).status()).isEqualTo(400);
        assertThat(patch(Map.of("email", "not an address")).status()).isEqualTo(400);
    }

    @Test
    void whatIsAlreadyTakenIsRefused() {
        assertThat(patch(Map.of("email", "taken@example.com")).status()).isEqualTo(409);
        assertThat(patch(Map.of("username", "taken")).status()).isEqualTo(409);
    }

    /** Two names that differ only in case are the same name to anyone reading them. */
    @Test
    void takenIgnoresCase() {
        assertThat(patch(Map.of("username", "TAKEN")).status()).isEqualTo(409);
        assertThat(patch(Map.of("email", "Taken@Example.com")).status()).isEqualTo(409);
    }

    /** Sending only one field changes only that field, rather than blanking the other. */
    @Test
    void whatIsNotSentIsNotChanged() {
        patch(Map.of("username", "renamed"));

        assertThat(patch(Map.of("email", "moved@example.com")).body())
                .containsEntry("username", "renamed")
                .containsEntry("email", "moved@example.com");
    }

    /**
     * A token left behind on a shared machine should not be enough to take the account with
     * it, so the password is asked for again even though the caller is already signed in.
     */
    @Test
    void changingAPasswordNeedsTheOldOne() {
        Response wrong = http.postJson(
                "/settings/account/password",
                Map.of("currentPassword", "not it at all", "newPassword", "a whole new one", "client", "WEB"),
                "Authorization",
                auth());

        assertThat(wrong.status()).isEqualTo(403);

        Response right = http.postJson(
                "/settings/account/password",
                Map.of("currentPassword", PASSWORD, "newPassword", "a whole new one", "client", "WEB"),
                "Authorization",
                auth());

        // Answers with a session rather than 204: the change ends every session the old
        // password could have left behind, and hands the caller its replacement.
        assertThat(right.status()).isEqualTo(200);
        assertThat(login("reader@example.com", PASSWORD).status()).isEqualTo(401);
        assertThat(login("reader@example.com", "a whole new one").status()).isEqualTo(200);
    }

    @Test
    void theExportCarriesTheAccountAndItsShelves() {
        Response response = http.get("/settings/account/export", "Authorization", auth());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body()).containsKeys("exportedAt", "account", "entries", "connectedAccounts", "activity");

        @SuppressWarnings("unchecked")
        Map<String, Object> account = (Map<String, Object>) response.body().get("account");
        assertThat(account).containsEntry("email", "reader@example.com");
        // A copy of someone's data is not a copy of their keys.
        assertThat(response.body().toString()).doesNotContain("accessToken");
    }

    @Test
    void deletingNeedsThePasswordAndThenLeavesNothing() {
        assertThat(http.deleteJson("/settings/account", Map.of("password", "wrong"), "Authorization", auth())
                        .status())
                .isEqualTo(403);
        assertThat(users.count()).isEqualTo(2);

        assertThat(http.deleteJson("/settings/account", Map.of("password", PASSWORD), "Authorization", auth())
                        .status())
                .isEqualTo(204);

        assertThat(users.existsByEmailIgnoreCase("reader@example.com")).isFalse();
        // The other account is untouched.
        assertThat(users.existsByEmailIgnoreCase("taken@example.com")).isTrue();
        assertThat(login("reader@example.com", PASSWORD).status()).isEqualTo(401);
    }

    @Test
    void signedOutCallersAreRefusedEverywhere() {
        assertThat(http.get("/settings/account/export").status()).isEqualTo(401);
        assertThat(http.patchJson("/settings/account", Map.of("username", "nobody")).status())
                .isEqualTo(401);
        assertThat(http.deleteJson("/settings/account", Map.of("password", PASSWORD)).status())
                .isEqualTo(401);
    }

    private Response login(String email, String password) {
        return http.postJson("/auth/login", Map.of("email", email, "password", password, "client", "WEB"));
    }
}
