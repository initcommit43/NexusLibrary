package dev.nexus.core;

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

/**
 * Sign-up is the only moment an address is examined — nothing in this app sends a confirmation
 * mail — so this covers what actually reaches the endpoint, and that a refusal arrives as a
 * field error the form can print under the box rather than a bare 400.
 */
class RegistrationHardeningIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    @Test
    void anOrdinaryAddressStillRegisters() {
        assertThat(register("reader@example.com", "reader").status()).isEqualTo(201);
        assertThat(users.findByEmail("reader@example.com")).isPresent();
    }

    @Test
    void aThrowawayAddressIsRefused() {
        Response refused = register("reader@mailinator.com", "reader");

        assertThat(refused.status()).isEqualTo(400);
        assertThat(refused.fieldErrors()).containsKey("email");
        assertThat(users.findByEmail("reader@mailinator.com")).isEmpty();
    }

    /** A 400 rather than the 409 a taken address gets: this one would never work. */
    @Test
    void aRefusalIsNotAConflict() {
        assertThat(register("reader@mailinator.com", "reader").status()).isNotEqualTo(409);
    }

    @Test
    void anAddressWithNoDomainIsRefused() {
        Response refused = register("reader@localhost", "reader");

        assertThat(refused.status()).isEqualTo(400);
        assertThat(users.findByEmail("reader@localhost")).isEmpty();
    }

    /**
     * The address is refused before the account is looked for, so a throwaway address that
     * happens to be registered is told what is wrong with it rather than that it is taken —
     * which would also answer a question about who has an account here.
     */
    @Test
    void aRefusedAddressIsNeverToldItIsTaken() {
        Response refused = register("someone@mailinator.com", "first");

        assertThat(refused.status()).isEqualTo(400);
        assertThat(String.valueOf(refused.body().get("message"))).doesNotContain("already registered");
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
