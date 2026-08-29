package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.modules.games.IgdbClient;
import dev.nexus.modules.games.SteamAchievementsClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * A game's achievements on its own page, for a title nobody here has imported.
 *
 * <p>The sync path never runs for such a game — it walks a user's library — so the list has
 * to be found from what the page already knows about the title.
 */
class MediaAchievementsIntegrationTest extends PostgresIntegrationTest {

    private static final String APP_ID = "292030";
    private static final String STEAM_LINK = "https://store.steampowered.com/app/" + APP_ID + "/The_Witcher_3/";

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    SteamAchievementsClient steamAchievements;

    @Autowired
    TrackableItemRepository items;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");

        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameDetail(anyString())).thenReturn(Optional.of(gameWith(STEAM_LINK)));
        when(steamAchievements.fetchSchema(anyString())).thenReturn(schema());
    }

    @Test
    void theListIsFetchedForAGameNobodyTracks() {
        openThePage();

        List<Map<String, Object>> found = achievements();

        assertThat(found).hasSize(1);
        assertThat(found.getFirst()).containsEntry("id", "ACH_ONE").containsEntry("name", "First One");
        verify(steamAchievements, times(1)).fetchSchema(APP_ID);
    }

    /** The appid is an id in the store link IGDB already handed over, not a title match. */
    @Test
    void theSteamMappingIsKeptOnceItIsRead() {
        openThePage();
        achievements();

        assertThat(ExternalIds.read(item(), Provider.STEAM)).contains(APP_ID);
    }

    @Test
    void theListIsFetchedOncePerGameHoweverManyReadItsPage() {
        openThePage();

        achievements();
        achievements();

        verify(steamAchievements, times(1)).fetchSchema(anyString());
    }

    @Test
    void aGameWithNoStoreLinkNeverReachesSteam() {
        when(igdbClient.findGameDetail(anyString())).thenReturn(Optional.of(gameWith("https://thewitcher.com")));
        openThePage();

        assertThat(achievements()).isEmpty();
        verify(steamAchievements, never()).fetchSchema(anyString());
    }

    /**
     * Plenty of games have no achievements, and Steam's budget is one key shared by every
     * user of the app — so the empty answer is remembered rather than asked for again.
     */
    @Test
    void aGameWithoutAchievementsIsNotAskedAboutAgain() {
        when(steamAchievements.fetchSchema(anyString())).thenReturn(List.of());
        openThePage();

        assertThat(achievements()).isEmpty();
        assertThat(achievements()).isEmpty();

        verify(steamAchievements, times(1)).fetchSchema(anyString());
    }

    /** Opening the page is what caches the item and its detail; the list follows it. */
    private void openThePage() {
        http.get("/catalog/media/IGDB/" + GamesTestData.BOTW_ID, "Authorization", "Bearer " + token);
    }

    /** What the page asks for once it is open. */
    private List<Map<String, Object>> achievements() {
        return http.get(
                        "/catalog/media/IGDB/" + GamesTestData.BOTW_ID + "/achievements",
                        "Authorization",
                        "Bearer " + token)
                .list();
    }

    private TrackableItem item() {
        return items.findBySourceAndExternalId(Source.IGDB, GamesTestData.BOTW_ID).orElseThrow();
    }

    private Map<String, Object> gameWith(String url) {
        Map<String, Object> game = GamesTestData.botw();
        game.put("websites", List.of(Map.of("url", url, "type", Map.of("type", "Steam"))));
        return game;
    }

    private List<Map<String, Object>> schema() {
        return List.of(Map.of(
                "name", "ACH_ONE",
                "displayName", "First One",
                "description", "Did the first thing",
                "icon", "https://cdn.steam/one.jpg",
                "icongray", "https://cdn.steam/one_gray.jpg",
                "hidden", 0));
    }
}
