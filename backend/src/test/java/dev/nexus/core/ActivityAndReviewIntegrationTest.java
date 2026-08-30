package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.ProviderActivity;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.ReviewRepository;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class ActivityAndReviewIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @Autowired
    AppUserRepository users;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    ActivityRepository activities;

    @Autowired
    ReviewRepository reviews;

    @Autowired
    ProviderActivityRepository imported;

    private HttpTestClient http;
    private String token;
    private long entryId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "player@example.com", "player");
        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));

        entryId = ((Number) track("PLANNING").body().get("id")).longValue();
    }

    // --- activity ---------------------------------------------------------

    @Test
    void trackingSomethingRecordsThatItWasAdded() {
        List<Map<String, Object>> feed = feed();

        assertThat(feed).hasSize(1);
        assertThat(feed.getFirst()).containsEntry("type", "ADDED");
        assertThat(feed.getFirst()).containsEntry("title", "The Legend of Zelda: Breath of the Wild");
    }

    @Test
    void aStatusChangeRecordsBothSidesOfTheMove() {
        patch(Map.of("status", "COMPLETED"));

        Map<String, Object> latest = feed().getFirst();

        assertThat(latest).containsEntry("type", "STATUS_CHANGE");
        assertThat(payloadOf(latest)).containsEntry("from", "PLANNING").containsEntry("to", "COMPLETED");
    }

    @Test
    void ratingRecordsAnActivity() {
        patch(Map.of("rating", 85));

        Map<String, Object> latest = feed().getFirst();

        assertThat(latest).containsEntry("type", "RATED");
        assertThat(payloadOf(latest)).containsEntry("to", "85");
    }

    @Test
    void progressRecordsItsUnitSoTheFeedCanReadNaturally() {
        patch(Map.of("progressCurrent", 320, "progressUnit", "MINUTES"));

        Map<String, Object> latest = feed().getFirst();

        assertThat(latest).containsEntry("type", "PROGRESS");
        assertThat(payloadOf(latest)).containsEntry("to", "320").containsEntry("unit", "MINUTES");
    }

    @Test
    void oneEditTouchingSeveralFieldsRecordsOneActivityEach() {
        patch(Map.of("status", "COMPLETED", "rating", 90, "progressCurrent", 100, "progressUnit", "MINUTES"));

        List<String> types = feed().stream().map(a -> (String) a.get("type")).toList();

        assertThat(types).contains("STATUS_CHANGE", "RATED", "PROGRESS", "ADDED");
    }

    /** A re-import re-submits the same values; the feed must not fill up with non-events. */
    @Test
    void resubmittingUnchangedValuesRecordsNothing() {
        patch(Map.of("status", "COMPLETED"));
        long afterFirstEdit = activities.count();

        patch(Map.of("status", "COMPLETED"));

        assertThat(activities.count()).isEqualTo(afterFirstEdit);
    }

    @Test
    void theFeedOnlyEverShowsYourOwnActivity() {
        String otherToken = registerAndGetToken(http, "other@example.com", "other");

        assertThat(http.get("/activity", "Authorization", "Bearer " + otherToken).list())
                .isEmpty();
        assertThat(feed()).isNotEmpty();
    }

    // --- reviews ----------------------------------------------------------

    @Test
    void writingAReviewStoresItAndRecordsActivity() {
        started();

        Response written = writeReview("A genuinely excellent game.", false);

        assertThat(written.status()).isEqualTo(200);
        assertThat(written.body()).containsEntry("body", "A genuinely excellent game.");
        assertThat(feed().getFirst()).containsEntry("type", "REVIEWED");
    }

    @Test
    void writingAgainReplacesTheReviewRatherThanAddingASecond() {
        started();
        writeReview("First take.", false);
        writeReview("Considered take.", true);

        assertThat(reviews.count()).isEqualTo(1);

        Response fetched = http.get("/entries/" + entryId + "/review", "Authorization", "Bearer " + token);
        assertThat(fetched.body()).containsEntry("body", "Considered take.");
        assertThat(fetched.body()).containsEntry("containsSpoilers", true);
    }

    /** Only the first review is news; edits would otherwise flood the feed. */
    @Test
    void editingAReviewDoesNotRecordASecondActivity() {
        started();
        writeReview("First take.", false);
        long afterFirst = activities.count();

        writeReview("Considered take.", false);

        assertThat(activities.count()).isEqualTo(afterFirst);
    }

    @Test
    void anEmptyReviewIsRejected() {
        started();

        assertThat(writeReview("   ", false).status()).isEqualTo(400);
    }

    @Test
    void aReviewCanBeDeleted() {
        started();
        writeReview("Regrettable opinion.", false);

        assertThat(http.delete("/entries/" + entryId + "/review", "Authorization", "Bearer " + token)
                        .status())
                .isEqualTo(204);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void anEntryWithNoReviewIsNotFound() {
        assertThat(http.get("/entries/" + entryId + "/review", "Authorization", "Bearer " + token)
                        .status())
                .isEqualTo(404);
    }

    @Test
    void anotherUserCannotReadWriteOrDeleteYourReview() {
        started();
        writeReview("Private thoughts.", false);
        String otherToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        assertThat(http.get("/entries/" + entryId + "/review", "Authorization", "Bearer " + otherToken)
                        .status())
                .isEqualTo(404);
        assertThat(http.putJson(
                                "/entries/" + entryId + "/review",
                                Map.of("body", "Vandalism.", "containsSpoilers", false),
                                "Authorization",
                                "Bearer " + otherToken)
                        .status())
                .isEqualTo(404);
        assertThat(http.delete("/entries/" + entryId + "/review", "Authorization", "Bearer " + otherToken)
                        .status())
                .isEqualTo(404);

        assertThat(reviews.count()).isEqualTo(1);
    }

    /**
     * Planning to play something is not an opinion of it. Every other shelf means the reader
     * started, which is what a review is written from.
     */
    @Test
    void aTitleNobodyHasStartedCannotBeReviewedYet() {
        assertThat(writeReview("Looks good from here.", false).status()).isEqualTo(409);
        assertThat(reviews.count()).isZero();
    }

    @Test
    void aDroppedTitleCanStillBeReviewed() {
        patch(Map.of("status", "DROPPED"));

        assertThat(writeReview("Gave up on it, and here is why.", false).status())
                .isEqualTo(200);
    }

    @Test
    void reviewAndActivityEndpointsRequireAuthentication() {
        assertThat(http.get("/activity").status()).isEqualTo(401);
        assertThat(http.get("/entries/" + entryId + "/review").status()).isEqualTo(401);
        assertThat(http.putJson("/entries/" + entryId + "/review", Map.of("body", "x"))
                        .status())
                .isEqualTo(401);
    }

    // --- imported activity ------------------------------------------------

    /**
     * A reader has one history. What AniList recorded before any of this existed belongs on
     * the same feed as what they did here, said in the words the provider said it in.
     */
    @Test
    void importedEventsShareTheFeedWithWhatWasDoneHere() {
        importedEvent("watched episode", "5", LocalDate.now());

        assertThat(feed())
                .anySatisfy(event -> {
                    assertThat(event).containsEntry("type", "EXTERNAL");
                    assertThat(event).containsEntry("title", GamesTestData.botw().get("name"));
                    assertThat(payloadOf(event)).containsEntry("progress", "5");
                });
    }

    /** Newest first, whichever half of the feed a row came from. */
    @Test
    void bothHalvesOfTheFeedAreInOneOrder() {
        importedEvent("watched episode", "5", LocalDate.now().minusDays(2));
        patch(Map.of("status", "IN_PROGRESS"));

        assertThat(feed().getFirst()).containsEntry("type", "STATUS_CHANGE");
        assertThat(feed().getLast()).containsEntry("type", "EXTERNAL");
    }

    @Test
    void anImportedEventCanBeForgottenAndIsGoneFromTheMapToo() {
        importedEvent("watched episode", "5", LocalDate.now());
        String id = (String) feed().stream()
                .filter(event -> "EXTERNAL".equals(event.get("type")))
                .findFirst()
                .orElseThrow()
                .get("id");

        assertThat(forget(id).status()).isEqualTo(204);
        assertThat(feed()).noneSatisfy(event -> assertThat(event).containsEntry("type", "EXTERNAL"));
        assertThat(imported.count()).isZero();
    }

    @Test
    void anEventOfYourOwnCanBeForgotten() {
        String id = (String) feed().getFirst().get("id");

        assertThat(forget(id).status()).isEqualTo(204);
        assertThat(feed()).noneSatisfy(event -> assertThat(event).containsEntry("id", id));
    }

    /** An id that is not the caller's is answered as one that does not exist. */
    @Test
    void anotherReadersEventCannotBeForgotten() {
        String id = (String) feed().getFirst().get("id");
        String stranger = registerAndGetToken(http, "someone@example.com", "someone");

        Response refused = http.delete("/activity/" + id, "Authorization", "Bearer " + stranger);

        assertThat(refused.status()).isEqualTo(404);
        assertThat(feed()).isNotEmpty();
    }

    @Test
    void nonsenseIdsAreNotFoundRatherThanAServerError() {
        assertThat(forget("own:not-a-number").status()).isEqualTo(404);
        assertThat(forget("imported:9999").status()).isEqualTo(404);
    }

    // --- helpers ----------------------------------------------------------

    /** One event as an import would have written it, against the fixture's own title. */
    private void importedEvent(String status, String progress, LocalDate day) {
        Long userId = users.findByEmail("player@example.com").orElseThrow().getId();
        Long itemId = entries.findById(entryId).orElseThrow().getItem().getId();

        imported.save(new ProviderActivity(
                userId, Provider.ANILIST, "event-" + day + "-" + progress, itemId, day, status, progress));
    }

    private Response forget(String feedId) {
        return http.delete("/activity/" + feedId, "Authorization", "Bearer " + token);
    }

    /** Moves the fixture off the planning shelf, which is where a review becomes writable. */
    private void started() {
        patch(Map.of("status", "IN_PROGRESS"));
    }

    private Response track(String status) {
        return http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", status),
                "Authorization",
                "Bearer " + token);
    }

    private Response patch(Map<String, ?> changes) {
        return http.patchJson("/entries/" + entryId, changes, "Authorization", "Bearer " + token);
    }

    private Response writeReview(String body, boolean spoilers) {
        return http.putJson(
                "/entries/" + entryId + "/review",
                Map.of("body", body, "containsSpoilers", spoilers),
                "Authorization",
                "Bearer " + token);
    }

    private List<Map<String, Object>> feed() {
        return http.get("/activity", "Authorization", "Bearer " + token).list();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payloadOf(Map<String, Object> activity) {
        return (Map<String, Object>) activity.get("payload");
    }
}
