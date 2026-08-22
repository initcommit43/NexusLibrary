package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.ReviewRepository;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
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
        Response written = writeReview("A genuinely excellent game.", false);

        assertThat(written.status()).isEqualTo(200);
        assertThat(written.body()).containsEntry("body", "A genuinely excellent game.");
        assertThat(feed().getFirst()).containsEntry("type", "REVIEWED");
    }

    @Test
    void writingAgainReplacesTheReviewRatherThanAddingASecond() {
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
        writeReview("First take.", false);
        long afterFirst = activities.count();

        writeReview("Considered take.", false);

        assertThat(activities.count()).isEqualTo(afterFirst);
    }

    @Test
    void anEmptyReviewIsRejected() {
        assertThat(writeReview("   ", false).status()).isEqualTo(400);
    }

    @Test
    void aReviewCanBeDeleted() {
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

    @Test
    void reviewAndActivityEndpointsRequireAuthentication() {
        assertThat(http.get("/activity").status()).isEqualTo(401);
        assertThat(http.get("/entries/" + entryId + "/review").status()).isEqualTo(401);
        assertThat(http.putJson("/entries/" + entryId + "/review", Map.of("body", "x"))
                        .status())
                .isEqualTo(401);
    }

    // --- helpers ----------------------------------------------------------

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
