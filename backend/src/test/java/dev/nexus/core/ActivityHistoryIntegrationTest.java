package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.modules.anime.AniListClient;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
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

/** The map at the head of a profile: which days saw something, and how much. */
class ActivityHistoryIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    AniListClient anilistClient;

    private HttpTestClient http;
    private String token;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime(21)));
        when(anilistClient.findMediaById(eq("30013"))).thenReturn(List.of(anime(30013)));
        when(anilistClient.findMediaById(eq("30002"))).thenReturn(List.of(manga(30002)));

        token = registerAndGetToken(http, "reader@example.com", "reader");
        today = LocalDate.now();
    }

    @Test
    void aFreshReaderHasNoHistoryAtAll() {
        assertThat(history()).isEmpty();
    }

    /** Starting and finishing are two things that happened, even on the same day. */
    @Test
    void aDayCountsWhatWasStartedAndWhatWasFinished() {
        dated(trackAnime("21"), today.minusDays(3), today.minusDays(3));

        assertThat(history()).containsExactly(Map.entry(today.minusDays(3).toString(), 2));
    }

    @Test
    void daysWithNothingOnThemAreNotSent() {
        dated(trackAnime("21"), today.minusDays(5), null);
        dated(trackAnime("30013"), today.minusDays(1), null);

        assertThat(history())
                .containsExactly(
                        Map.entry(today.minusDays(5).toString(), 1),
                        Map.entry(today.minusDays(1).toString(), 1));
    }

    @Test
    void anythingOlderThanTheWindowIsLeftOut() {
        dated(trackAnime("21"), today.minusWeeks(60), null);

        assertThat(history()).isEmpty();
    }

    /**
     * Steam knows how long a game was played and never when, so a library of them carries no
     * dates: counting games would draw a blank year over a shelf someone lives in.
     */
    @Test
    void gamesAreLeftOffTheMapAltogether() {
        dated(trackGame(), today.minusDays(2), today.minusDays(2));

        assertThat(history()).isEmpty();
    }

    /** The map is pointed at one module at a time, and counts only what that module holds. */
    @Test
    void askingForOneKindLeavesTheOthersOut() {
        dated(trackAnime("21"), today.minusDays(4), null);
        dated(trackManga("30002"), today.minusDays(6), null);

        assertThat(history("&mediaTypes=ANIME"))
                .containsExactly(Map.entry(today.minusDays(4).toString(), 1));
        assertThat(history("&mediaTypes=MANGA"))
                .containsExactly(Map.entry(today.minusDays(6).toString(), 1));
        assertThat(history("&mediaTypes=ANIME,MANGA")).hasSize(2);
    }

    /** Asking for a shelf that keeps no dates is asking for nothing, not for everything. */
    @Test
    void askingForGamesAloneCountsNothing() {
        dated(trackAnime("21"), today.minusDays(4), null);
        dated(trackGame(), today.minusDays(4), null);

        assertThat(history("&mediaTypes=GAME")).isEmpty();
    }

    private List<Map.Entry<String, Integer>> history() {
        return history("");
    }

    private List<Map.Entry<String, Integer>> history(String scope) {
        Response response =
                http.get("/activity/history?weeks=30" + scope, "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);

        return response.list().stream()
                .map(day -> Map.entry((String) day.get("date"), ((Number) day.get("amount")).intValue()))
                .toList();
    }

    private void dated(long entryId, LocalDate started, LocalDate finished) {
        Map<String, Object> body = new HashMap<>();
        body.put("startedAt", started == null ? null : started.toString());
        body.put("finishedAt", finished == null ? null : finished.toString());

        assertThat(http.patchJson("/entries/" + entryId, body, "Authorization", "Bearer " + token)
                        .status())
                .isEqualTo(200);
    }

    private long trackAnime(String externalId) {
        return track("ANILIST", externalId);
    }

    private long trackManga(String externalId) {
        return track("ANILIST", externalId);
    }

    private long trackGame() {
        return track("IGDB", GamesTestData.BOTW_ID);
    }

    private long track(String source, String externalId) {
        Response response = http.postJson(
                "/entries",
                Map.of("source", source, "externalId", externalId, "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
        assertThat(response.status()).isEqualTo(201);
        return ((Number) response.body().get("id")).longValue();
    }

    private static Map<String, Object> anime(int id) {
        return media(id, "ANIME");
    }

    private static Map<String, Object> manga(int id) {
        return media(id, "MANGA");
    }

    private static Map<String, Object> media(int id, String type) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", id);
        media.put("type", type);
        media.put("status", "FINISHED");
        media.put("episodes", 12);
        media.put("chapters", 100);
        media.put("title", new HashMap<>(Map.of("english", type + " " + id)));
        return media;
    }
}
