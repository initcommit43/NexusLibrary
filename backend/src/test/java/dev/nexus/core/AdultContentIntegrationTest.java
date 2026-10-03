package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.Notification;
import dev.nexus.core.domain.NotificationRepository;
import dev.nexus.core.domain.NotificationType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.auth.AppUserRepository;
import dev.nexus.modules.anime.AniListClient;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.AuthenticatedTest;
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

/**
 * What an adult title does to each surface that could show it, for a reader who has adult
 * titles on and one who has them off. Over real HTTP with only the sources' transport stubbed.
 */
class AdultContentIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    AniListClient aniListClient;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    NotificationRepository notifications;

    @Autowired
    AppUserRepository users;

    private HttpTestClient http;

    /** Born 1990, with adult titles switched on. */
    private String shown;

    /** Born 1990, left at the default of hidden. */
    private String hidden;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        shown = AuthenticatedTest.registerAndGetToken(http, "shown@example.com", "shown");
        hidden = AuthenticatedTest.registerAndGetToken(http, "hidden@example.com", "hidden");
        assertThat(http.patchJson("/settings/content", Map.of("showAdult", true), "Authorization", "Bearer " + shown)
                        .status())
                .isEqualTo(200);

        // Stubbed on the flag itself, so a call that asked the wrong question gets the wrong answer.
        when(igdbClient.searchGames(anyString(), anyInt(), eq(false))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.searchGames(anyString(), anyInt(), eq(true)))
                .thenReturn(List.of(GamesTestData.botw(), GamesTestData.eroticGame()));
        when(igdbClient.findGameById(GamesTestData.BOTW_ID)).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(GamesTestData.EROTIC_ID)).thenReturn(List.of(GamesTestData.eroticGame()));
    }

    private Response track(String token, String externalId) {
        return http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", externalId, "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);
    }

    private Response setShowAdult(String token, boolean on) {
        return http.patchJson("/settings/content", Map.of("showAdult", on), "Authorization", "Bearer " + token);
    }

    private Response get(String path, String token) {
        return http.get(path, "Authorization", "Bearer " + token);
    }

    private static List<String> titles(Response response, String key) {
        List<Map<String, Object>> rows = key == null ? response.list() : rows(response.body().get(key));
        return rows.stream().map(row -> String.valueOf(row.get("title"))).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        return (List<Map<String, Object>>) value;
    }

    @Test
    void searchAsksTheSourceForAdultTitlesOnlyForAReaderWhoHasThemOn() {
        assertThat(titles(get("/catalog/search?mediaType=GAME&q=game", hidden), null))
                .containsExactly("The Legend of Zelda: Breath of the Wild");
        assertThat(titles(get("/catalog/search?mediaType=GAME&q=game", shown), null)).hasSize(2);
    }

    @Test
    void discoverAsksTheSourceTheSameWay() {
        when(igdbClient.discoverGames(any(), any(), any(), anyInt(), anyInt(), eq(false)))
                .thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.discoverGames(any(), any(), any(), anyInt(), anyInt(), eq(true)))
                .thenReturn(List.of(GamesTestData.botw(), GamesTestData.eroticGame()));

        assertThat(titles(get("/catalog/discover?mediaType=GAME", hidden), "items")).hasSize(1);
        assertThat(titles(get("/catalog/discover?mediaType=GAME", shown), "items")).hasSize(2);
    }

    /** Not found rather than forbidden, so the route cannot confirm that an adult title exists. */
    @Test
    void anAdultTitlesPageIsNotFoundForAReaderWhoHasThemOff() {
        Response opened = get("/catalog/media/IGDB/" + GamesTestData.EROTIC_ID, shown);
        assertThat(opened.status()).isEqualTo(200);
        assertThat(opened.body()).containsEntry("adult", true);

        assertThat(get("/catalog/media/IGDB/" + GamesTestData.EROTIC_ID, hidden).status()).isEqualTo(404);
        assertThat(get("/catalog/media/IGDB/" + GamesTestData.BOTW_ID, hidden).body()).containsEntry("adult", false);
    }

    /** Refused with the same 404 as the title's page, so tracking cannot confirm the id. */
    @Test
    void trackingAnAdultTitleWithThemOffIsNotFound() {
        assertThat(track(hidden, GamesTestData.EROTIC_ID).status()).isEqualTo(404);
        assertThat(entries.count()).isZero();

        assertThat(track(shown, GamesTestData.EROTIC_ID).status()).isEqualTo(201);
    }

    /** Switching them off hides an adult entry without deleting it, and switching back restores it. */
    @Test
    void anAdultEntryIsHiddenRatherThanLostWhenTheSettingGoesOff() {
        track(shown, GamesTestData.BOTW_ID);
        Response tracked = track(shown, GamesTestData.EROTIC_ID);
        Object adultEntryId = tracked.body().get("id");
        assertThat(tracked.body()).containsEntry("adult", true);
        assertThat(titles(get("/entries", shown), null)).hasSize(2);

        setShowAdult(shown, false);

        assertThat(titles(get("/entries", shown), null)).containsExactly("The Legend of Zelda: Breath of the Wild");
        assertThat(get("/entries/" + adultEntryId, shown).status()).isEqualTo(404);
        assertThat(entries.count()).isEqualTo(2);

        setShowAdult(shown, true);
        assertThat(titles(get("/entries", shown), null)).hasSize(2);
        assertThat(get("/entries/" + adultEntryId, shown).status()).isEqualTo(200);
    }

    /** Left out in the query, so the feed still fills the page it was asked for. */
    @Test
    void theFeedLeavesAdultTitlesOutAndStillFillsItsPage() {
        track(shown, GamesTestData.BOTW_ID);
        track(shown, GamesTestData.EROTIC_ID);
        assertThat(titles(get("/activity?limit=1", shown), null)).containsExactly("An Adult Game");

        setShowAdult(shown, false);

        assertThat(titles(get("/activity?limit=1", shown), null))
                .containsExactly("The Legend of Zelda: Breath of the Wild");
    }

    /** The unread count and the list agree, or the badge would count a row that is not there. */
    @Test
    void notificationsAboutAdultTitlesAreLeftOutOfTheListAndTheCount() {
        track(shown, GamesTestData.BOTW_ID);
        track(shown, GamesTestData.EROTIC_ID);
        Long userId = users.findByEmail("shown@example.com").orElseThrow().getId();
        for (String id : List.of(GamesTestData.BOTW_ID, GamesTestData.EROTIC_ID)) {
            notifications.save(new Notification(
                    userId,
                    items.findBySourceAndExternalId(Source.IGDB, id).orElseThrow(),
                    NotificationType.TITLE_ADDED,
                    "test:" + id,
                    Map.of()));
        }
        assertThat(get("/notifications", shown).body()).containsEntry("unread", 2);

        setShowAdult(shown, false);

        Response waiting = get("/notifications", shown);
        assertThat(waiting.body()).containsEntry("unread", 1);
        assertThat(titles(waiting, "items")).containsExactly("The Legend of Zelda: Breath of the Wild");
    }

    @Test
    void aStudiosAdultWorksAreLeftOutForAReaderWhoHasThemOff() {
        when(aniListClient.fetchStudioWorks(eq("7"), anyInt(), anyInt()))
                .thenReturn(new AniListClient.StudioPage(
                        "Studio",
                        List.of(
                                Map.of("id", 1, "type", "ANIME", "title", Map.of("romaji", "Clean"), "isAdult", false),
                                Map.of("id", 2, "type", "ANIME", "title", Map.of("romaji", "Adult"), "isAdult", true)),
                        false));

        assertThat(titles(get("/catalog/studios/ANILIST/7", hidden), "items")).containsExactly("Clean");
        assertThat(titles(get("/catalog/studios/ANILIST/7", shown), "items")).hasSize(2);
    }
}
