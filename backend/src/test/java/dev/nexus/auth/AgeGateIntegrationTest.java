package dev.nexus.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/** The age floor on registration, and what it records once someone is past it. */
class AgeGateIntegrationTest extends PostgresIntegrationTest {

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

    private Response register(String email, String username, LocalDate dateOfBirth) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("username", username);
        payload.put("password", "correct-horse-battery");
        payload.put("client", "WEB");
        payload.put("acceptedTerms", true);
        payload.put("dateOfBirth", dateOfBirth == null ? null : dateOfBirth.toString());
        return http.postJson("/auth/register", payload);
    }

    /** The server's day, so a run around midnight in another zone does not flip a birthday. */
    private static LocalDate yearsAgo(int years) {
        return AgePolicy.today().minusYears(years);
    }

    @Test
    void someoneOldEnoughIsRegisteredAndTheirDateIsKept() {
        assertThat(register("reader@example.com", "reader", yearsAgo(20)).status()).isEqualTo(201);

        assertThat(users.findByEmail("reader@example.com"))
                .get()
                .extracting(AppUser::getDateOfBirth)
                .isEqualTo(yearsAgo(20));
    }

    @Test
    void someoneTooYoungIsRefusedAndNoAccountIsLeftBehind() {
        Response response = register("young@example.com", "young", yearsAgo(14));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("dateOfBirth");
        assertThat(users.findByEmail("young@example.com")).isEmpty();
    }

    /** Sixteen tomorrow is fifteen today, which is the case a year subtraction gets wrong. */
    @Test
    void theDayBeforeTheSixteenthBirthdayIsStillTooYoung() {
        assertThat(register("almost@example.com", "almost", yearsAgo(16).plusDays(1)).status())
                .isEqualTo(400);
    }

    @Test
    void turningSixteenTodayIsOldEnough() {
        assertThat(register("today@example.com", "today", yearsAgo(16)).status()).isEqualTo(201);
    }

    @Test
    void aDateInTheFutureIsRefused() {
        Response response = register("future@example.com", "future", AgePolicy.today().plusDays(2));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("dateOfBirth");
    }

    @Test
    void aDateBefore1900IsRefused() {
        Response response = register("old@example.com", "old", LocalDate.of(1899, 12, 31));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("dateOfBirth");
        assertThat(users.findByEmail("old@example.com")).isEmpty();
    }

    @Test
    void registeringWithNoDateAtAllIsRefused() {
        assertThat(register("nodate@example.com", "nodate", null).status()).isEqualTo(400);
        assertThat(users.findByEmail("nodate@example.com")).isEmpty();
    }
}
