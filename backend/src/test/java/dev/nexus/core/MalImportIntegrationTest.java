package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.importing.ExternalAccountService;
import dev.nexus.modules.anime.AniListClient;
import dev.nexus.modules.anime.MalClient;
import dev.nexus.modules.anime.MalUserNotFoundException;
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
 * The MAL import, end to end — the one with a real matching layer.
 *
 * <p>Its point is the resolution promise: MAL entries land on their AniList canonicals, by
 * the idMal join where AniList knows the id and by title where it does not, and never as a
 * duplicate of a title an AniList import already brought in. The unmatched report is the
 * intended landing place for what neither route settles.
 */
class MalImportIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    MalClient malClient;

    @MockitoBean
    AniListClient anilistClient;

    @Autowired
    UserEntryRepository entries;

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

        when(malClient.fetchAnimeList(anyString())).thenReturn(List.of());
        when(malClient.fetchMangaList(anyString())).thenReturn(List.of());
        when(anilistClient.findMediaByMalIds(any(), anyCollection())).thenReturn(List.of());
        when(anilistClient.searchMedia(any(), anyString(), anyInt())).thenReturn(List.of());
        when(anilistClient.findMediaByIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    void connectingStoresTheUsernameOnceMalConfirmsIt() {
        Response connected =
                http.postJson("/integrations/mal/connect", Map.of("username", "reader"), "Authorization", "Bearer " + token);

        assertThat(connected.status()).isEqualTo(200);
        assertThat(connected.body()).containsEntry("provider", "MAL");
        assertThat(connected.body()).containsEntry("externalUserId", "reader");
    }

    /** A typo fails at connect time with the reason, not at import time with a mystery. */
    @Test
    void connectingAnUnknownUsernameFailsWithAdvice() {
        doThrow(new MalUserNotFoundException("ghost")).when(malClient).probeUser("ghost");

        Response refused =
                http.postJson("/integrations/mal/connect", Map.of("username", "ghost"), "Authorization", "Bearer " + token);

        assertThat(refused.status()).isEqualTo(404);
        assertThat(String.valueOf(refused.body().get("message"))).contains("ghost");
        assertThat(accounts.listFor(userId)).isEmpty();
    }

    @Test
    void entriesLandOnTheirAniListCanonicalsThroughTheIdJoin() {
        connectMal();
        when(malClient.fetchAnimeList("reader")).thenReturn(List.of(malAnimeRow(20, "Naruto", 220, "completed", 220, 8)));
        when(malClient.fetchMangaList("reader")).thenReturn(List.of(malMangaRow(2, "Berserk", 0, "reading", 120, 10)));

        // AniList knows both MAL ids, so the whole list resolves by the join.
        when(anilistClient.findMediaByMalIds(any(), anyCollection())).thenAnswer(call -> {
            MediaType type = call.getArgument(0);
            return type == MediaType.ANIME
                    ? List.of(anilistMedia(21, 20, "ANIME", "Naruto"))
                    : List.of(anilistMedia(30013, 2, "MANGA", "Berserk"));
        });
        when(anilistClient.findMediaByIds(anyCollection()))
                .thenReturn(List.of(anilistMedia(21, 20, "ANIME", "Naruto"), anilistMedia(30013, 2, "MANGA", "Berserk")));

        Response job = runImport();

        assertThat(job.body().get("state")).isEqualTo("COMPLETE");
        assertThat(reportOf(job)).containsEntry("created", 2);

        List<UserEntry> mine = entries.findByUserIdOrderByUpdatedAtDesc(userId);
        assertThat(mine).hasSize(2);
        assertThat(mine).allSatisfy(entry -> {
            assertThat(entry.getImportedFrom()).isEqualTo(Provider.MAL);
            assertThat(entry.getItem().getSource().name()).isEqualTo("ANILIST");
        });
        // MAL's 8-of-10 lands as 80-of-100: one internal scale, whatever the source used.
        assertThat(mine).extracting(UserEntry::getRating).contains((short) 80);
    }

    /** What the join misses goes to the title search, judged by the ported matching rules. */
    @Test
    void aTitleAniListHasNoMalIdForFallsBackToTheSearch() {
        connectMal();
        when(malClient.fetchAnimeList("reader"))
                .thenReturn(List.of(malAnimeRow(999, "Fullmetal Alchemist Brotherhood", 64, "completed", 64, 9)));

        // The join finds nothing; the search offers a candidate with no MAL id but an
        // agreeing title and episode count — which the matcher accepts.
        when(anilistClient.searchMedia(MediaType.ANIME, "Fullmetal Alchemist Brotherhood", 10))
                .thenReturn(List.of(anilistMedia(5114, null, "ANIME", "Fullmetal Alchemist: Brotherhood")));
        when(anilistClient.findMediaByIds(anyCollection()))
                .thenReturn(List.of(anilistMedia(5114, null, "ANIME", "Fullmetal Alchemist: Brotherhood")));

        Response job = runImport();

        assertThat(reportOf(job)).containsEntry("created", 1);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(userId))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getItem().getExternalId()).isEqualTo("5114"));
    }

    /** Neither route settles it: the unmatched report is the landing place, not an error. */
    @Test
    void whatNothingMatchesLandsInTheUnmatchedReport() {
        connectMal();
        when(malClient.fetchAnimeList("reader"))
                .thenReturn(List.of(malAnimeRow(777, "Some Obscure Special", 1, "completed", 1, 0)));

        Response job = runImport();

        assertThat(job.body().get("state")).isEqualTo("COMPLETE");
        assertThat(reportOf(job)).containsEntry("created", 0);
        assertThat(reportOf(job).get("unmatched"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .hasSize(1);
    }

    /**
     * The acceptance line from the plan: no duplicate canonicals. A title the AniList
     * import already brought in arrives from MAL as an update to the same entry — and the
     * provenance of the original stays what it was.
     */
    @Test
    void aMalImportNeverDuplicatesWhatAniListAlreadyImported() {
        // The AniList import happened first: same work, canonical id 21.
        accounts.connect(userId, Provider.ANILIST, "reader", "tok", null, null);
        when(anilistClient.fetchList(anyString(), any(), anyString())).thenReturn(List.of());
        when(anilistClient.fetchList("reader", MediaType.ANIME, "tok"))
                .thenReturn(List.of(anilistListRow(21, 20, "ANIME", 100)));
        when(anilistClient.findMediaByIds(anyCollection())).thenReturn(List.of(anilistMedia(21, 20, "ANIME", "Naruto")));
        Response first = awaitJob(startImport("ANILIST"));
        assertThat(reportOf(first)).containsEntry("created", 1);

        // Now the same work arrives from MAL, resolving through the join onto id 21.
        connectMal();
        when(malClient.fetchAnimeList("reader")).thenReturn(List.of(malAnimeRow(20, "Naruto", 220, "completed", 220, 8)));
        when(anilistClient.findMediaByMalIds(any(), anyCollection()))
                .thenAnswer(call -> call.getArgument(0) == MediaType.ANIME
                        ? List.of(anilistMedia(21, 20, "ANIME", "Naruto"))
                        : List.of());

        Response second = runImport();

        assertThat(reportOf(second)).containsEntry("created", 0);
        assertThat(reportOf(second)).containsEntry("updated", 1);
        assertThat(entries.count()).isEqualTo(1);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(userId))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getImportedFrom()).isEqualTo(Provider.ANILIST));
    }

    private void connectMal() {
        Response connected =
                http.postJson("/integrations/mal/connect", Map.of("username", "reader"), "Authorization", "Bearer " + token);
        assertThat(connected.status()).isEqualTo(200);
    }

    private Response runImport() {
        return awaitJob(startImport("MAL"));
    }

    private String startImport(String provider) {
        Response started = http.post("/integrations/" + provider + "/import", "Authorization", "Bearer " + token);
        assertThat(started.status()).isEqualTo(200);
        return String.valueOf(started.body().get("id"));
    }

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

    private static Map<String, Object> malAnimeRow(
            int id, String title, int episodes, String status, int watched, int score) {

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("title", title);
        node.put("num_episodes", episodes);
        node.put("alternative_titles", Map.of("en", title, "ja", ""));

        Map<String, Object> listStatus = new HashMap<>();
        listStatus.put("status", status);
        listStatus.put("score", score);
        listStatus.put("num_episodes_watched", watched);

        return Map.of("node", node, "list_status", listStatus);
    }

    private static Map<String, Object> malMangaRow(
            int id, String title, int chapters, String status, int read, int score) {

        Map<String, Object> node = new HashMap<>();
        node.put("id", id);
        node.put("title", title);
        node.put("num_chapters", chapters);
        node.put("alternative_titles", Map.of("en", title, "ja", ""));

        Map<String, Object> listStatus = new HashMap<>();
        listStatus.put("status", status);
        listStatus.put("score", score);
        listStatus.put("num_chapters_read", read);

        return Map.of("node", node, "list_status", listStatus);
    }

    private static Map<String, Object> anilistListRow(int id, Integer idMal, String type, int progress) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "CURRENT");
        row.put("progress", progress);
        row.put("media", anilistMedia(id, idMal, type, "Naruto"));
        return row;
    }

    private static Map<String, Object> anilistMedia(int id, Integer idMal, String type, String title) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", id);
        media.put("idMal", idMal);
        media.put("type", type);
        media.put("status", "FINISHED");
        media.put("episodes", type.equals("ANIME") ? 64 : null);
        media.put("chapters", type.equals("MANGA") ? 380 : null);
        media.put("title", new HashMap<>(Map.of("english", title, "romaji", title)));
        return media;
    }
}
