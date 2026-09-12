package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.auth.AppUserRepository;
import dev.nexus.core.activity.ActivityRecorder;
import dev.nexus.core.domain.ActivityRepository;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.ProviderActivity;
import dev.nexus.core.domain.ProviderActivityRepository;
import dev.nexus.core.domain.UserEntryRepository;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Limit;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Emptying whole shelves at once.
 *
 * <p>A bulk delete is the one request where a missing owner scope does not fail loudly or
 * return the wrong row — it takes somebody else's library with it and answers 200. That is
 * what most of this file is for.
 */
class ClearLibraryIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @MockitoBean
    AniListClient anilistClient;

    @Autowired
    UserEntryRepository entries;

    @Autowired
    ActivityRepository activity;

    @Autowired
    AppUserRepository users;

    @Autowired
    ProviderActivityRepository imported;

    @Autowired
    ActivityRecorder recorder;

    private HttpTestClient http;
    private String ownerToken;
    private String strangerToken;
    private long ownerId;
    private long strangerId;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(igdbClient.findGameById(anyString())).thenReturn(List.of(GamesTestData.botw()));
        when(anilistClient.findMediaById(eq("21"))).thenReturn(List.of(anime()));

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        strangerToken = registerAndGetToken(http, "stranger@example.com", "stranger");
        ownerId = users.findByEmail("owner@example.com").orElseThrow().getId();
        strangerId = users.findByEmail("stranger@example.com").orElseThrow().getId();

        trackGame(ownerToken);
        trackAnime(ownerToken);
        trackGame(strangerToken);
    }

    @Test
    void clearingOneMediumLeavesTheOther() {
        Response cleared = clear(ownerToken, List.of("GAME"));

        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).containsEntry("entries", 1);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(ownerId))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getItem().getMediaType().name()).isEqualTo("ANIME"));
    }

    /** The claim this whole file exists to defend. */
    @Test
    void clearingOneLibraryDoesNotTouchAnother() {
        clear(ownerToken, List.of("GAME", "ANIME", "MANGA", "MOVIE", "SHOW", "BOOK"));

        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(ownerId)).isEmpty();
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(strangerId)).hasSize(1);
    }

    /**
     * Activity hangs off the reader and the catalogue item, never the entry, so nothing in the
     * database removes it when a shelf is emptied. Without this the reader keeps a feed of
     * things that happened to titles they no longer track.
     */
    @Test
    void clearingTakesTheHistoryWithIt() {
        assertThat(activity.findByUserIdOrderByCreatedAtDesc(ownerId, org.springframework.data.domain.Limit.of(50)))
                .isNotEmpty();

        clear(ownerToken, List.of("GAME", "ANIME"));

        assertThat(activity.findByUserIdOrderByCreatedAtDesc(ownerId, org.springframework.data.domain.Limit.of(50)))
                .isEmpty();
        assertThat(activity.findByUserIdOrderByCreatedAtDesc(strangerId, org.springframework.data.domain.Limit.of(50)))
                .isNotEmpty();
    }

    /**
     * "Everything" is the reader's word, not a default the server supplies. An empty list is a
     * request that lost its meaning somewhere, and guessing at it deletes a library.
     */
    @Test
    void anEmptyListIsRefused() {
        Response refused = http.postJson(
                "/entries/clear", Map.of("mediaTypes", List.of()), "Authorization", "Bearer " + ownerToken);

        assertThat(refused.status()).isEqualTo(400);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(ownerId)).hasSize(2);
    }

    @Test
    void clearingRefusesAnUnauthenticatedCaller() {
        Response refused = http.postJson("/entries/clear", Map.of("mediaTypes", List.of("GAME")));

        assertThat(refused.status()).isEqualTo(401);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(ownerId)).hasSize(2);
    }

    /** Emptying an already empty shelf is a no-op, not a 404: the reader asked for a state. */
    @Test
    void clearingNothingSucceeds() {
        Response cleared = clear(strangerToken, List.of("BOOK"));

        assertThat(cleared.status()).isEqualTo(200);
        assertThat(cleared.body()).containsEntry("entries", 0);
        assertThat(entries.findByUserIdOrderByUpdatedAtDesc(strangerId)).hasSize(1);
    }

    /**
     * The activity map reads imported history as well as entry dates, and that history is keyed to
     * the catalogue item, not the entry — so it survived a wipe that stopped at the entries.
     */
    @Test
    void clearingTakesImportedHistoryOffTheMap() {
        long anime = itemIdOf(ownerId, "ANIME");
        imported.save(new ProviderActivity(ownerId, Provider.ANILIST, "owner-1", anime, LocalDate.now(), "CURRENT", "5"));
        imported.save(new ProviderActivity(strangerId, Provider.ANILIST, "stranger-1", anime, LocalDate.now(), "CURRENT", "2"));

        clear(ownerToken, List.of("ANIME", "MANGA"));

        assertThat(historyOf(ownerId)).isZero();
        assertThat(historyOf(strangerId)).as("another reader's history is theirs").isEqualTo(1);
    }

    /** "142 arrived from AniList" belongs to no title, so the title-scoped delete never saw it. */
    @Test
    void clearingEveryShelfOfAProviderTakesItsImportEvents() {
        recorder.ran(ownerId, Provider.ANILIST, 3, List.of());

        clear(ownerToken, List.of("ANIME", "MANGA"));

        assertThat(runsOf(ownerId)).isZero();
    }

    /**
     * One AniList run brought anime and manga in together. Clearing only the anime shelf must keep
     * it on record, because it still describes the manga the reader kept.
     */
    @Test
    void clearingOneOfAProvidersShelvesKeepsItsImportEvents() {
        recorder.ran(ownerId, Provider.ANILIST, 3, List.of());

        clear(ownerToken, List.of("ANIME"));

        assertThat(runsOf(ownerId)).isEqualTo(1);
    }

    @Test
    void clearingLeavesAnotherReadersImportEventsAlone() {
        recorder.ran(strangerId, Provider.STEAM, 2, List.of());

        clear(ownerToken, List.of("GAME", "ANIME", "MANGA", "MOVIE", "SHOW", "BOOK"));

        assertThat(runsOf(strangerId)).isEqualTo(1);
    }

    private long itemIdOf(long userId, String mediaType) {
        return entries.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(entry -> entry.getItem().getMediaType().name().equals(mediaType))
                .findFirst().orElseThrow().getItem().getId();
    }

    private long historyOf(long userId) {
        return imported.findAll().stream().filter(row -> row.getUserId().equals(userId)).count();
    }

    private long runsOf(long userId) {
        return activity.findByUserIdOrderByCreatedAtDesc(userId, Limit.of(100)).stream()
                .filter(row -> row.getItem() == null)
                .count();
    }

    private Response clear(String token, List<String> mediaTypes) {
        return http.postJson(
                "/entries/clear",
                Map.of("mediaTypes", mediaTypes),
                "Authorization",
                "Bearer " + token);
    }

    private void trackGame(String token) {
        http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", GamesTestData.BOTW_ID, "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
    }

    private void trackAnime(String token) {
        http.postJson(
                "/entries",
                Map.of("source", "ANILIST", "externalId", "21", "status", "IN_PROGRESS"),
                "Authorization",
                "Bearer " + token);
    }

    private static Map<String, Object> anime() {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("type", "ANIME");
        media.put("status", "FINISHED");
        media.put("episodes", 26);
        media.put("title", new HashMap<>(Map.of("english", "Sekirei")));
        return media;
    }
}
