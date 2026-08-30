package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.ProviderActivity;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.importing.ExternalAccountService;
import dev.nexus.modules.anime.AniListClient;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.modules.games.SteamAchievementsClient;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The AniList import, end to end.
 *
 * <p>Its point is that it touches anime and manga and nothing else: two connections that
 * both import must stay entirely separate runs, or pressing one imports the other's library.
 */
class AniListImportIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    SteamAchievementsClient steamAchievements;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    AppUserRepository users;

    @Autowired
    ExternalAccountService accounts;

    @Autowired
    ProviderActivityRepository providerActivity;

    private HttpTestClient http;
    private String token;
    private Long userId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");
        userId = users.findByEmail("reader@example.com").orElseThrow().getId();
        accounts.connect(userId, Provider.ANILIST, "reader", "tok", null, null);

        when(anilistClient.fetchList(anyString(), any(), anyString())).thenReturn(List.of());
        when(anilistClient.fetchList("reader", MediaType.ANIME, "tok")).thenReturn(List.of(listRow(21, "ANIME", 430)));
        when(anilistClient.fetchList("reader", MediaType.MANGA, "tok")).thenReturn(List.of(listRow(30013, "MANGA", 90)));
        when(anilistClient.findMediaByIds(anyCollection()))
                .thenReturn(List.of(media(21, "ANIME"), media(30013, "MANGA")));

        // Every import is followed by the activity sync, so every test in here runs one.
        when(anilistClient.viewerId(anyString())).thenReturn(7);
        when(anilistClient.fetchActivity(anyInt(), anyInt(), anyString()))
                .thenReturn(new AniListClient.ActivityPage(List.of(), false));
    }

    /**
     * The reason the stream is imported at all: a list entry knows the day something was
     * started and the day it was finished, and the stream knows every day in between.
     */
    @Test
    void theActivityStreamComesInBehindTheLibrary() {
        LocalDate watched = LocalDate.now().minusDays(4);
        when(anilistClient.fetchActivity(7, 1, "tok"))
                .thenReturn(new AniListClient.ActivityPage(
                        List.of(activity(1, 21, watched, "5"), activity(2, 21, watched.minusDays(1), "4")),
                        false));

        awaitFollowUp(runImport());

        assertThat(providerActivity.findAll())
                .extracting(ProviderActivity::getHappenedOn)
                .containsExactlyInAnyOrder(watched, watched.minusDays(1));
    }

    /** A square on the map has to belong to something the shelf can explain. */
    @Test
    void eventsAboutTitlesThatAreNotOnTheShelfAreLeftOut() {
        when(anilistClient.fetchActivity(7, 1, "tok"))
                .thenReturn(new AniListClient.ActivityPage(
                        List.of(activity(1, 999, LocalDate.now(), "1")), false));

        awaitFollowUp(runImport());

        assertThat(providerActivity.count()).isZero();
    }

    /** The stream is walked newest first, so a second run reaches its own history and stops. */
    @Test
    void aSecondImportBringsTheSameActivityInOnlyOnce() {
        when(anilistClient.fetchActivity(7, 1, "tok"))
                .thenReturn(new AniListClient.ActivityPage(
                        List.of(activity(1, 21, LocalDate.now(), "5")), false));

        awaitFollowUp(runImport());
        awaitFollowUp(runImport());

        assertThat(providerActivity.count()).isEqualTo(1);
    }

    @Test
    void bothListsLandAsEntriesOfTheirOwnKind() {
        Response job = runImport();

        assertThat(job.body().get("state")).isEqualTo("COMPLETE");
        assertThat(reportOf(job)).containsEntry("created", 2);

        List<UserEntry> mine = entries.findByUserIdOrderByUpdatedAtDesc(userId);
        assertThat(mine).hasSize(2);
        assertThat(mine).extracting(entry -> entry.getItem().getMediaType())
                .containsExactlyInAnyOrder(MediaType.ANIME, MediaType.MANGA);
    }

    /** An AniList entry names its canonical item, so nothing has to be guessed at. */
    @Test
    void everyEntryResolvesWithoutAnUnmatchedReport() {
        assertThat(reportOf(runImport()).get("unmatched")).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .isEmpty();
    }

    @Test
    void progressIsReportedAsTheListIsWalked() {
        Response job = runImport();

        assertThat(job.body()).containsEntry("total", 2);
        assertThat(job.body()).containsEntry("processed", 2);
        assertThat(job.body()).containsEntry("provider", "ANILIST");
        assertThat(job.body()).containsEntry("kind", "IMPORT");
    }

    /**
     * Caching unseen titles is the long part of an import and the writes are the short one,
     * so the run has to say which it is doing. Reporting only the writes left a reader
     * watching a still label for the whole wait and a bar that crossed in one step.
     */
    @Test
    void theCatalogueFetchIsCountedUnderItsOwnPhase() {
        List<String> phasesWhileFetching = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(anilistClient.findMediaByIds(anyCollection())).thenAnswer(call -> {
            phasesWhileFetching.add(currentPhase());
            return List.of(media(21, "ANIME"), media(30013, "MANGA"));
        });

        Response job = runImport();

        assertThat(phasesWhileFetching).containsExactly("MATCHING");
        assertThat(job.body()).containsEntry("phase", "IMPORTING");
    }

    /**
     * A run that fails against a dead upstream must say whose outage it is and what the
     * service said for itself — "please try again" is an instruction to retry something
     * that cannot succeed. And because the import is one transaction, the failure keeps
     * nothing, so the message must not pretend anything was saved.
     */
    @Test
    void anAniListOutageIsReportedInAniListsOwnTerms() {
        when(anilistClient.findMediaByIds(anyCollection()))
                .thenThrow(new dev.nexus.modules.anime.AniListUnavailableException(
                        "AniList responded with 403",
                        403,
                        "The AniList API has been temporarily disabled due to severe stability issues."));

        Response job = runImport();

        assertThat(job.body().get("state")).isEqualTo("FAILED");
        // As data, not only prose: the client's outage banner keys off this field.
        assertThat(job.body().get("unavailableService")).isEqualTo("AniList");
        assertThat(String.valueOf(job.body().get("message")))
                .contains("AniList is not answering")
                .contains("nothing from this run was saved")
                .contains("temporarily disabled due to severe stability issues")
                .doesNotContain("Please try again.");
        assertThat(entries.count()).isZero();
    }

    /** Nothing running is answered with an empty body, which a client has to survive. */
    @Test
    void thereIsNoCurrentJobOnceTheImportHasFinished() {
        // Including the activity sync behind it: what the indicator watches is whether
        // anything at all is still running, not whether the import itself is done.
        awaitFollowUp(runImport());

        Response current = http.get("/integrations/jobs/current", "Authorization", "Bearer " + token);
        assertThat(current.status()).isEqualTo(200);
        assertThat(current.rawBody()).isNullOrEmpty();
    }

    /**
     * The whole point of separating the runs: importing one connection must not reach for
     * another's catalogue, or the reader gets a library they did not ask for.
     */
    @Test
    void importingAniListNeverTouchesSteamOrIgdb() {
        runImport();

        verify(igdbClient, never()).findGamesBySteamAppIds(anyCollection());
        verify(igdbClient, never()).findGamesByIds(anyCollection());
        verify(steamAchievements, never()).fetch(anyString(), anyString());
    }

    /** A second run finds the same library, so it adds nothing and touches nothing. */
    @Test
    void aSecondRunLeavesWhatHasNotChangedAlone() {
        runImport();
        Response second = runImport();

        assertThat(reportOf(second)).containsEntry("created", 0);
        assertThat(reportOf(second)).containsEntry("updated", 0);
        assertThat(entries.count()).isEqualTo(2);
        assertThat(items.count()).isEqualTo(2);
    }

    /**
     * The other half of that: what did move must move here too. Finishing a series on AniList
     * and finding it still listed as watching afterwards is the import having done half its
     * job, whatever the entry looked like before.
     */
    @Test
    void statusAndProgressChangedOnAniListLandOnTheNextImport() {
        runImport();

        Map<String, Object> finished = listRow(21, "ANIME", 1000);
        finished.put("status", "COMPLETED");
        when(anilistClient.fetchList("reader", MediaType.ANIME, "tok")).thenReturn(List.of(finished));

        Response second = runImport();

        assertThat(reportOf(second)).containsEntry("updated", 1);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(userId))
                .filteredOn(entry -> entry.getItem().getMediaType() == MediaType.ANIME)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getStatus()).isEqualTo(TrackingStatus.COMPLETED);
                    assertThat(entry.getProgressCurrent()).isEqualTo(1000);
                });
    }

    @Test
    void anEntryRemembersThatAniListPutItThere() {
        runImport();

        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(userId))
                .allSatisfy(entry -> assertThat(entry.getImportedFrom()).isEqualTo(Provider.ANILIST));
    }

    /** The activity sync is started by the import and runs behind it, on its own job. */
    private void awaitFollowUp(Response job) {
        Object followUp = job.body().get("followUpJobId");
        assertThat(followUp).as("the import should have started an activity sync").isNotNull();
        assertThat(awaitJob(String.valueOf(followUp)).body().get("state")).isEqualTo("COMPLETE");
    }

    /** One event as AniList reports it: seconds since the epoch, and the title it was about. */
    private static Map<String, Object> activity(int id, int mediaId, LocalDate day, String progress) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("createdAt", day.atTime(12, 0).atZone(ZoneId.systemDefault()).toEpochSecond());
        row.put("status", "watched episode");
        row.put("progress", progress);
        row.put("media", new HashMap<>(Map.of("id", mediaId, "type", "ANIME")));
        return row;
    }

    private Response runImport() {
        Response started = http.post("/integrations/ANILIST/import", "Authorization", "Bearer " + token);
        assertThat(started.status()).isEqualTo(200);
        return awaitJob(String.valueOf(started.body().get("id")));
    }

    /** What the indicator would see right now, read from the import's own thread. */
    private String currentPhase() {
        Response current = http.get("/integrations/jobs/current", "Authorization", "Bearer " + token);
        Object phase = current.body().get("phase");
        return phase == null ? null : String.valueOf(phase);
    }

    /** The run answers immediately and works in the background, so a test has to wait for it. */
    private Response awaitJob(String jobId) {
        Instant deadline = Instant.now().plusSeconds(20);

        while (Instant.now().isBefore(deadline)) {
            Response job = http.get("/integrations/jobs/" + jobId, "Authorization", "Bearer " + token);
            if (job.status() != 200 || !"RUNNING".equals(job.body().get("state"))) {
                return job;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("Import job never finished: " + jobId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> reportOf(Response job) {
        return (Map<String, Object>) job.body().get("report");
    }

    /** Mutable on purpose: a test changes what AniList says between one run and the next. */
    private static Map<String, Object> listRow(int id, String type, int progress) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "CURRENT");
        row.put("progress", progress);
        row.put("score", 85);
        row.put("media", media(id, type));
        return row;
    }

    private static Map<String, Object> media(int id, String type) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", id);
        media.put("idMal", id);
        media.put("type", type);
        media.put("status", "FINISHED");
        media.put("episodes", 1000);
        media.put("chapters", 100);
        media.put("title", new HashMap<>(Map.of("english", type + " " + id)));
        return media;
    }
}
