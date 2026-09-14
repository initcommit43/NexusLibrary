package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.auth.AgePolicy;
import dev.nexus.support.AuthenticatedTest;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** The adult-content switches, and who may move them. */
class ContentPreferenceIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    private HttpTestClient http;

    /** Born 1990, so the 18+ switch is theirs. */
    private String adult;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        adult = AuthenticatedTest.registerAndGetToken(http, "grown@example.com", "grown");
    }

    private String sixteenYearOld() {
        Response response = http.postJson(
                "/auth/register",
                Map.of(
                        "email", "teen@example.com",
                        "username", "teen",
                        "password", AuthenticatedTest.PASSWORD,
                        "client", "WEB",
                        "dateOfBirth", AgePolicy.today().minusYears(16).toString(),
                        "acceptedTerms", true));
        assertThat(response.status()).isEqualTo(201);
        return response.accessToken();
    }

    private Response get(String token) {
        return http.get("/settings/content", "Authorization", "Bearer " + token);
    }

    private Response change(String token, Map<String, ?> change) {
        return http.patchJson("/settings/content", change, "Authorization", "Bearer " + token);
    }

    @Test
    void adultTitlesStartHiddenAndBlurredWithoutARowBeingWritten() {
        assertThat(get(adult).body())
                .containsEntry("showAdult", false)
                .containsEntry("blurAdult", true)
                .containsEntry("adultAllowed", true)
                .containsEntry("dateOfBirthSet", true);
    }

    @Test
    void anAdultCanTurnThemOnAndTheBlurStaysItsOwnSetting() {
        assertThat(change(adult, Map.of("showAdult", true)).status()).isEqualTo(200);
        assertThat(change(adult, Map.of("blurAdult", false)).body())
                .containsEntry("showAdult", true)
                .containsEntry("blurAdult", false);
    }

    @Test
    void anAccountUnderEighteenMayNotTurnThemOnButMaySetTheBlur() {
        String teen = sixteenYearOld();

        assertThat(get(teen).body()).containsEntry("adultAllowed", false);
        assertThat(change(teen, Map.of("showAdult", true)).status()).isEqualTo(403);
        assertThat(get(teen).body()).containsEntry("showAdult", false);
        assertThat(change(teen, Map.of("blurAdult", false)).status()).isEqualTo(200);
    }

    /** An account from before the question has no known age, which is never an adult one. */
    @Test
    void anAccountWithNoDateOfBirthIsNotAllowed() {
        jdbc.update("UPDATE app_user SET date_of_birth = NULL WHERE email = 'grown@example.com'");

        assertThat(get(adult).body())
                .containsEntry("adultAllowed", false)
                .containsEntry("dateOfBirthSet", false);
        assertThat(change(adult, Map.of("showAdult", true)).status()).isEqualTo(403);
    }

    /** A stored "on" does not survive the age it was allowed for going away. */
    @Test
    void theAgeIsCheckedOnReadAsWellAsOnWrite() {
        change(adult, Map.of("showAdult", true));
        jdbc.update("UPDATE app_user SET date_of_birth = NULL WHERE email = 'grown@example.com'");

        assertThat(get(adult).body()).containsEntry("showAdult", false);
    }

    @Test
    void signedOutCallersAreRefused() {
        assertThat(http.get("/settings/content").status()).isEqualTo(401);
        assertThat(http.patchJson("/settings/content", Map.of("blurAdult", false)).status()).isEqualTo(401);
    }
}
