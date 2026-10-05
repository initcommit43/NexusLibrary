package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.modules.anime.AniListClient;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Finishing something by watching it: the last episode is the end of the thing, and an entry
 * left at twelve of twelve under "watching" is a shelf disagreeing with itself.
 */
class EntryCompletionIntegrationTest extends PostgresIntegrationTest {

    private static final int EPISODES = 12;

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    private HttpTestClient http;
    private String token;
    private long entryId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime()));

        token = registerAndGetToken(http, "reader@example.com", "reader");
        entryId = track();
    }

    @Test
    void reachingTheLastEpisodeCompletesTheEntry() {
        Response updated = progress(EPISODES, null);

        assertThat(updated.body()).containsEntry("status", "COMPLETED");
    }

    /** And stamps the day, which is what puts it on the reader's map. */
    @Test
    void completingByProgressDatesTheEntryToday() {
        assertThat(progress(EPISODES, null).body())
                .containsEntry("finishedAt", LocalDate.now().toString());
    }

    @Test
    void progressShortOfTheEndLeavesTheStatusAlone() {
        Response updated = progress(EPISODES - 1, null);

        assertThat(updated.body()).containsEntry("status", "IN_PROGRESS");
        assertThat(updated.body()).containsEntry("finishedAt", null);
    }

    /** Dropping something on its last episode is a thing people do, and they said so. */
    @Test
    void aStatusSentWithTheProgressIsWhatTheEntryBecomes() {
        Response updated = progress(EPISODES, "DROPPED");

        assertThat(updated.body()).containsEntry("status", "DROPPED");
    }

    /**
     * The reader's last word stands. Putting a finished thing back to watching is a decision,
     * and every later change to the entry — a note, a rating, the same twelve of twelve
     * written again — must not quietly take it back for them.
     */
    @Test
    void puttingAFinishedEntryBackToWatchingSticks() {
        progress(EPISODES, null);
        patch(Map.of("status", "IN_PROGRESS"));

        assertThat(progress(EPISODES, null).body()).containsEntry("status", "IN_PROGRESS");
        assertThat(patch(Map.of("notes", "rewatching with a friend")).body())
                .containsEntry("status", "IN_PROGRESS");
    }

    /** A date the reader gave is theirs; finishing does not move it to today. */
    @Test
    void aFinishDateAlreadySetIsKept() {
        LocalDate watched = LocalDate.now().minusDays(3);
        patch(Map.of("finishedAt", watched.toString()));

        assertThat(progress(EPISODES, null).body()).containsEntry("finishedAt", watched.toString());
    }

    /** Saying it is finished says it was seen to the end: every episode, on the day it was said. */
    @Test
    void markingCompletedFillsInTheProgressAndTheDay() {
        Response updated = patch(Map.of("status", "COMPLETED"));

        assertThat(updated.body()).containsEntry("progressCurrent", EPISODES);
        assertThat(updated.body()).containsEntry("progressMax", EPISODES);
        assertThat(updated.body()).containsEntry("progressUnit", "EPISODES");
        assertThat(updated.body()).containsEntry("finishedAt", LocalDate.now().toString());
    }

    /** The edit dialog sends the progress it was opened with alongside the new status. */
    @Test
    void markingCompletedOverTheOldProgressStillReachesTheEnd() {
        patch(Map.of("progressCurrent", 5));

        assertThat(patch(Map.of("status", "COMPLETED", "progressCurrent", 5)).body())
                .containsEntry("progressCurrent", EPISODES);
    }

    /** Tracking a title straight from the status menu goes through the same door. */
    @Test
    void trackingAsCompletedFillsInTheProgressToo() {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", "COMPLETED"),
                "Authorization",
                "Bearer " + token);

        assertThat(response.body()).containsEntry("progressCurrent", EPISODES);
        assertThat(response.body()).containsEntry("finishedAt", LocalDate.now().toString());
    }

    @Test
    void aFinishDateGivenWithTheStatusIsKept() {
        LocalDate watched = LocalDate.now().minusDays(3);

        assertThat(patch(Map.of("status", "COMPLETED", "finishedAt", watched.toString())).body())
                .containsEntry("finishedAt", watched.toString());
    }

    /** Only the move to completed fills anything in; a finished title edited later keeps what it says. */
    @Test
    void editingAnAlreadyCompletedEntryLeavesItsProgressAlone() {
        patch(Map.of("status", "COMPLETED"));

        assertThat(patch(Map.of("status", "COMPLETED", "progressCurrent", 3)).body())
                .containsEntry("progressCurrent", 3);
    }

    /** A source with no count yet leaves the entry's own total as the end to reach. */
    @Test
    void withNoCountFromTheSourceTheEntrysOwnTotalIsTheEnd() {
        Map<String, Object> airing = anime();
        airing.put("id", 22);
        airing.remove("episodes");
        when(anilistClient.findMediaById(eq("22"))).thenReturn(List.of(airing));
        Response tracked = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "22", "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
        long id = ((Number) tracked.body().get("id")).longValue();
        http.patchJson("/entries/" + id, Map.of("progressMax", 24, "progressUnit", "EPISODES"),
                "Authorization", "Bearer " + token);

        Response completed = http.patchJson(
                "/entries/" + id, Map.of("status", "COMPLETED"), "Authorization", "Bearer " + token);

        assertThat(completed.body()).containsEntry("progressCurrent", 24);
    }

    private Response progress(int watched, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("progressCurrent", watched);
        body.put("progressMax", EPISODES);
        if (status != null) {
            body.put("status", status);
        }
        return patch(body);
    }

    private Response patch(Map<String, Object> body) {
        Response response = http.patchJson("/entries/" + entryId, body, "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response;
    }

    private long track() {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
        assertThat(response.status()).isEqualTo(201);
        return ((Number) response.body().get("id")).longValue();
    }

    private static Map<String, Object> anime() {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("type", "ANIME");
        media.put("status", "FINISHED");
        media.put("episodes", EPISODES);
        media.put("title", new HashMap<>(Map.of("english", "Sekirei")));
        return media;
    }
}
