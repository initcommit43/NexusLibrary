package dev.nexus.auth.agreements;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * The documents a reader is held to, which of them apply where, and the rule that a version
 * bump puts a document back in front of the people who accepted the older text.
 */
class AgreementIntegrationTest extends PostgresIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @LocalServerPort
    int port;

    @Autowired
    AgreementProperties agreements;

    @Autowired
    AgreementAcceptanceRepository acceptances;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
    }

    // --- which documents apply where ---------------------------------------------------

    @Test
    void theWebIsOfferedTheCookieNoticeAndNeverTheEula() {
        Response response = http.get("/agreements?client=WEB");

        assertThat(response.status()).isEqualTo(200);
        assertThat(documents(response)).containsExactly("TERMS", "PRIVACY", "COOKIES");
    }

    /** A native client sets no cookie at all, so asking it to consent to one is meaningless. */
    @Test
    void theAppIsOfferedTheEulaAndNeverTheCookieNotice() {
        Response response = http.get("/agreements?client=NATIVE");

        assertThat(response.status()).isEqualTo(200);
        assertThat(documents(response)).containsExactly("TERMS", "PRIVACY", "EULA");
    }

    /** A sign-up form has to show what it is asking before anyone has an account. */
    @Test
    void theDocumentListIsReachableWithoutAnAccount() {
        assertThat(http.get("/agreements?client=WEB").status()).isEqualTo(200);
    }

    /** Which set to answer with is not a guess: a caller that says nothing is refused. */
    @Test
    void theDocumentListRefusesACallerThatDoesNotSayWhatItIs() {
        assertThat(http.get("/agreements").status()).isEqualTo(400);
    }

    @Test
    void eachDocumentSaysWhereItCanBeRead() {
        List<Map<String, Object>> body = http.get("/agreements?client=WEB").list();

        assertThat(body).allSatisfy(document -> {
            assertThat((String) document.get("url")).startsWith("/");
            assertThat((String) document.get("version")).isNotBlank();
        });
    }

    // --- registration ------------------------------------------------------------------

    /**
     * The old boolean named the terms and the privacy policy and nothing else, so that is all
     * it records. The cookie notice it never mentioned is left outstanding rather than being
     * accepted on the reader's behalf.
     */
    @Test
    void theOldBooleanRecordsTermsAndPrivacyAndInventsNothing() {
        Response session = registerLegacy("player@example.com", "player", "WEB");

        assertThat(session.status()).isEqualTo(201);
        assertThat(outstanding(session)).containsExactly("COOKIES");
    }

    @Test
    void acceptingEveryDocumentAtRegistrationLeavesNothingOutstanding() {
        Response session = http.postJson(
                "/auth/register",
                Map.of(
                        "email", "player@example.com",
                        "username", "player",
                        "password", PASSWORD,
                        "client", "WEB",
                        "dateOfBirth", "1990-01-01",
                        "acceptedTerms", true,
                        "acceptedAgreements", currentFor(Agreement.TERMS, Agreement.PRIVACY, Agreement.COOKIES)));

        assertThat(session.status()).isEqualTo(201);
        assertThat(outstanding(session)).isEmpty();
    }

    /** A client that predates the field keeps working, which is what v1 promised its callers. */
    @Test
    void registrationStillWorksWithoutTheNewField() {
        assertThat(registerLegacy("player@example.com", "player", "WEB").status())
                .isEqualTo(201);
    }

    @Test
    void registrationRefusesAVersionThatIsNotCurrent() {
        Response response = http.postJson(
                "/auth/register",
                Map.of(
                        "email", "player@example.com",
                        "username", "player",
                        "password", PASSWORD,
                        "client", "WEB",
                        "dateOfBirth", "1990-01-01",
                        "acceptedTerms", true,
                        "acceptedAgreements", List.of(Map.of("document", "TERMS", "version", "1999-01-01"))));

        assertThat(response.status()).isEqualTo(400);
        assertThat(users.findByEmail("player@example.com")).isEmpty();
    }

    // --- web and native are kept apart --------------------------------------------------

    /**
     * The deliberate product decision: the store listing is a different offer, so agreeing in
     * a browser says nothing about the app.
     */
    @Test
    void acceptingEverythingOnTheWebLeavesTheAppStillAsking() {
        registerLegacy("player@example.com", "player", "WEB");
        acceptAll(tokenOf(loginAs("player", "WEB")), "WEB");

        Response app = loginAs("player", "NATIVE");

        assertThat(outstanding(app)).containsExactly("TERMS", "PRIVACY", "EULA");
    }

    @Test
    void theAppIsNeverAskedForCookieConsent() {
        registerLegacy("player@example.com", "player", "NATIVE");

        assertThat(outstanding(loginAs("player", "NATIVE"))).doesNotContain("COOKIES");
    }

    // --- signing in with something outstanding ------------------------------------------

    /**
     * Accepting is itself an authenticated call, so a sign-in that refused would leave the
     * reader with no session to accept with and no way out of the loop.
     */
    @Test
    void signingInStillSucceedsWithSomethingOutstanding() {
        registerLegacy("player@example.com", "player", "WEB");

        Response session = loginAs("player", "WEB");

        assertThat(session.status()).isEqualTo(200);
        assertThat(session.accessToken()).isNotBlank();
        assertThat(outstanding(session)).isNotEmpty();
    }

    // --- accepting ----------------------------------------------------------------------

    @Test
    void acceptingClearsTheOutstandingSet() {
        registerLegacy("player@example.com", "player", "WEB");
        String token = tokenOf(loginAs("player", "WEB"));

        assertThat(acceptAll(token, "WEB").status()).isEqualTo(204);
        assertThat(documents(outstandingFor(token, "WEB"))).isEmpty();
    }

    @Test
    void acceptRefusesAVersionThatIsNotCurrent() {
        registerLegacy("player@example.com", "player", "WEB");
        String token = tokenOf(loginAs("player", "WEB"));

        Response response = http.postJson(
                "/agreements/accept",
                Map.of("client", "WEB", "agreements", List.of(Map.of("document", "COOKIES", "version", "1999-01-01"))),
                "Authorization",
                "Bearer " + token);

        assertThat(response.status()).isEqualTo(400);
        assertThat(documents(outstandingFor(token, "WEB"))).containsExactly("COOKIES");
    }

    @Test
    void acceptRefusesADocumentThatDoesNotApplyToThatClient() {
        registerLegacy("player@example.com", "player", "NATIVE");
        String token = tokenOf(loginAs("player", "NATIVE"));

        Response response = http.postJson(
                "/agreements/accept",
                Map.of("client", "NATIVE", "agreements", currentFor(Agreement.COOKIES)),
                "Authorization",
                "Bearer " + token);

        assertThat(response.status()).isEqualTo(400);
    }

    /** One refused pair records none of the request, rather than half of it. */
    @Test
    void oneRefusedPairRecordsNothingFromTheRequest() {
        registerLegacy("player@example.com", "player", "NATIVE");
        String token = tokenOf(loginAs("player", "NATIVE"));

        http.postJson(
                "/agreements/accept",
                Map.of(
                        "client",
                        "NATIVE",
                        "agreements",
                        List.of(
                                Map.of("document", "EULA", "version", version(Agreement.EULA)),
                                Map.of("document", "COOKIES", "version", version(Agreement.COOKIES)))),
                "Authorization",
                "Bearer " + token);

        assertThat(documents(outstandingFor(token, "NATIVE"))).contains("EULA");
    }

    @Test
    void acceptingTheSameTextTwiceLeavesOneRow() {
        registerLegacy("player@example.com", "player", "WEB");
        String token = tokenOf(loginAs("player", "WEB"));

        acceptAll(token, "WEB");
        acceptAll(token, "WEB");

        Long userId = users.findByEmail("player@example.com").orElseThrow().getId();
        assertThat(acceptances.findByUserIdAndPlatform(userId, AgreementPlatform.WEB))
                .hasSize(3);
    }

    @Test
    void outstandingNeedsASession() {
        assertThat(http.get("/agreements/outstanding?client=WEB").status()).isEqualTo(401);
    }

    @Test
    void acceptingNeedsASession() {
        Response response = http.postJson(
                "/agreements/accept", Map.of("client", "WEB", "agreements", currentFor(Agreement.COOKIES)));

        assertThat(response.status()).isEqualTo(401);
    }

    /** Object-level: one reader accepting says nothing about anybody else's account. */
    @Test
    void acceptingNeverClearsAnotherReadersOutstandingSet() {
        registerLegacy("one@example.com", "readerone", "WEB");
        registerLegacy("two@example.com", "readertwo", "WEB");

        acceptAll(tokenOf(loginAs("readerone", "WEB")), "WEB");

        assertThat(outstanding(loginAs("readertwo", "WEB"))).containsExactly("COOKIES");
    }

    // --- re-consent when a document changes ----------------------------------------------

    /**
     * The whole point of carrying the version: an acceptance of an older text does not answer
     * for the current one, and the old row stays where it is so it remains provable.
     */
    @Test
    void anAcceptanceOfAnOlderVersionIsOutstandingAgainAndSurvives() {
        registerLegacy("player@example.com", "player", "WEB");
        Long userId = users.findByEmail("player@example.com").orElseThrow().getId();
        acceptances.save(
                new AgreementAcceptance(userId, Agreement.COOKIES, AgreementPlatform.WEB, "1999-01-01", Instant.now()));

        String token = tokenOf(loginAs("player", "WEB"));
        assertThat(documents(outstandingFor(token, "WEB"))).containsExactly("COOKIES");

        acceptAll(token, "WEB");

        assertThat(acceptances.findByUserIdOrderByAcceptedAtDesc(userId))
                .filteredOn(row -> row.getDocument() == Agreement.COOKIES)
                .extracting(AgreementAcceptance::getVersion)
                .containsExactlyInAnyOrder("1999-01-01", version(Agreement.COOKIES));
    }

    // --- helpers ---------------------------------------------------------------------------

    private Response registerLegacy(String email, String username, String client) {
        return http.postJson(
                "/auth/register",
                Map.of(
                        "email", email,
                        "username", username,
                        "password", PASSWORD,
                        "client", client,
                        "dateOfBirth", "1990-01-01",
                        "acceptedTerms", true));
    }

    private Response loginAs(String username, String client) {
        return http.postJson("/auth/login", Map.of("login", username, "password", PASSWORD, "client", client));
    }

    private Response outstandingFor(String token, String client) {
        return http.get("/agreements/outstanding?client=" + client, "Authorization", "Bearer " + token);
    }

    private Response acceptAll(String token, String client) {
        AgreementPlatform platform = AgreementPlatform.valueOf(client);
        return http.postJson(
                "/agreements/accept",
                Map.of(
                        "client",
                        client,
                        "agreements",
                        currentFor(Agreement.forPlatform(platform).toArray(new Agreement[0]))),
                "Authorization",
                "Bearer " + token);
    }

    private List<Map<String, String>> currentFor(Agreement... documents) {
        return List.of(documents).stream()
                .map(document -> Map.of("document", document.name(), "version", version(document)))
                .toList();
    }

    private String version(Agreement document) {
        return agreements.versionOf(document);
    }

    private String tokenOf(Response session) {
        return session.accessToken();
    }

    @SuppressWarnings("unchecked")
    private List<String> outstanding(Response session) {
        List<Map<String, Object>> rows =
                (List<Map<String, Object>>) session.body().getOrDefault("outstandingAgreements", List.of());
        return rows.stream().map(row -> (String) row.get("document")).toList();
    }

    private List<String> documents(Response response) {
        return response.list().stream().map(row -> (String) row.get("document")).toList();
    }
}
