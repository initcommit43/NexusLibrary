package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.domain.ExternalIds;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.modules.games.AchievementItemSyncer;
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

class AchievementSyncIntegrationTest extends PostgresIntegrationTest {

    private static final String STEAM_ID = "76561198000000001";
    private static final String APP_ID = "220240";

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    SteamAchievementsClient steamAchievements;

    @Autowired
    AchievementItemSyncer syncer;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    TrackableItemRepository items;

    @Autowired
    AppUserRepository users;

    private Long entryId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        HttpTestClient http = new HttpTestClient(port);
        String token = registerAndGetToken(http, "player@example.com", "player");
        Long userId = users.findByEmail("player@example.com").orElseThrow().getId();

        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
        http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);

        UserEntry entry = entries.findByUserIdOrderByUpdatedAtDesc(userId).getFirst();
        entryId = entry.getId();

        TrackableItem item = entry.getItem();
        ExternalIds.record(item, Provider.STEAM, APP_ID);
        items.save(item);

        when(steamAchievements.fetch(anyString(), anyString())).thenReturn(Optional.of(playerAchievements()));
        when(steamAchievements.fetchSchema(anyString())).thenReturn(schema());
    }

    @Test
    void storesUnlockedAchievementsAgainstTheEntry() {
        assertThat(syncer.syncOne(entryId, STEAM_ID)).isTrue();

        Map<String, Object> progress = achievementsOf(entries.findById(entryId).orElseThrow());
        assertThat(unlockedOf(progress)).containsExactly("ACH_ONE");
        assertThat(progress).containsEntry("total", 2);
    }

    /** The catalogue is the same for every player, so it belongs on the shared item. */
    @Test
    void storesTheCatalogueWithIconsOnTheSharedItem() {
        syncer.syncOne(entryId, STEAM_ID);

        Object catalogue = entries.findById(entryId).orElseThrow().getItem().getMetadata().get("achievements");
        assertThat(catalogue).isInstanceOf(List.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) ((List<?>) catalogue).getFirst();
        assertThat(first).containsEntry("id", "ACH_ONE");
        assertThat(first).containsEntry("name", "First One");
        assertThat(first.get("icon")).isNotNull();
        assertThat(first.get("lockedIcon")).isNotNull();
    }

    /**
     * Steam enforces an undocumented request budget, so a library sync can run out
     * part-way. Skipping fresh games costs no request, which is what lets a retry finish
     * the remainder rather than spending the budget again on work already done.
     */
    @Test
    void aRecentlySyncedGameIsSkippedWithoutCallingSteam() {
        syncer.syncOne(entryId, STEAM_ID);
        verify(steamAchievements, times(1)).fetch(anyString(), anyString());

        assertThat(syncer.syncOne(entryId, STEAM_ID)).isFalse();

        // Still one: the second run never reached Steam at all.
        verify(steamAchievements, times(1)).fetch(anyString(), anyString());
    }

    @Test
    void theCatalogueIsFetchedOncePerGameRatherThanPerSync() {
        syncer.syncOne(entryId, STEAM_ID);
        stripSyncedAt();
        syncer.syncOne(entryId, STEAM_ID);

        verify(steamAchievements, times(1)).fetchSchema(anyString());
    }

    @Test
    void aGameWithoutAchievementsIsLeftAlone() {
        when(steamAchievements.fetch(anyString(), anyString())).thenReturn(Optional.empty());

        assertThat(syncer.syncOne(entryId, STEAM_ID)).isFalse();
        assertThat(entries.findById(entryId).orElseThrow().getProgressExtra()).isNull();
    }

    @Test
    void aGameWithNoSteamMappingIsNeverSynced() {
        TrackableItem item = entries.findById(entryId).orElseThrow().getItem();
        item.getMetadata().remove("externalIds");
        items.save(item);

        assertThat(syncer.syncOne(entryId, STEAM_ID)).isFalse();
        verify(steamAchievements, never()).fetch(anyString(), anyString());
    }

    @SuppressWarnings("unchecked")
    private List<String> unlockedOf(Map<String, Object> progress) {
        return (List<String>) progress.get("unlocked");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> achievementsOf(UserEntry entry) {
        return (Map<String, Object>) entry.getProgressExtra().get("achievements");
    }

    /** Ages the stamp so the next run treats the entry as stale. */
    @SuppressWarnings("unchecked")
    private void stripSyncedAt() {
        UserEntry entry = entries.findById(entryId).orElseThrow();
        Map<String, Object> extra = entry.getProgressExtra();
        ((Map<String, Object>) extra.get("achievements")).remove("syncedAt");
        entry.setProgressExtra(extra);
        entries.save(entry);
    }

    private List<Map<String, Object>> playerAchievements() {
        return List.of(
                Map.of("apiname", "ACH_ONE", "achieved", 1, "unlocktime", 1_700_000_000L),
                Map.of("apiname", "ACH_TWO", "achieved", 0, "unlocktime", 0));
    }

    private List<Map<String, Object>> schema() {
        return List.of(
                Map.of(
                        "name", "ACH_ONE",
                        "displayName", "First One",
                        "description", "Do the first thing",
                        "icon", "https://steam.test/one.jpg",
                        "icongray", "https://steam.test/one_grey.jpg",
                        "hidden", 0),
                Map.of(
                        "name", "ACH_TWO",
                        "displayName", "Second One",
                        "description", "Do the second thing",
                        "icon", "https://steam.test/two.jpg",
                        "icongray", "https://steam.test/two_grey.jpg",
                        "hidden", 1));
    }
}
