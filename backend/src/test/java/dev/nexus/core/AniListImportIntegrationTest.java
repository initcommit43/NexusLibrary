package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackableItemRepository;
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

    @Test
    void aSecondRunUpdatesRatherThanDuplicating() {
        runImport();
        Response second = runImport();

        assertThat(reportOf(second)).containsEntry("created", 0);
        assertThat(reportOf(second)).containsEntry("updated", 2);
        assertThat(entries.count()).isEqualTo(2);
        assertThat(items.count()).isEqualTo(2);
    }

    @Test
    void anEntryRemembersThatAniListPutItThere() {
        runImport();

        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(userId))
                .allSatisfy(entry -> assertThat(entry.getImportedFrom()).isEqualTo(Provider.ANILIST));
    }

    private Response runImport() {
        Response started = http.post("/integrations/ANILIST/import", "Authorization", "Bearer " + token);
        assertThat(started.status()).isEqualTo(200);
        return awaitJob(String.valueOf(started.body().get("id")));
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
