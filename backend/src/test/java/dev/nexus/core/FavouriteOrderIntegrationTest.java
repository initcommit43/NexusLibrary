package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

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

/** The arrangement a reader drags their favourites into, and who is allowed to write it. */
class FavouriteOrderIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    @Autowired
    UserEntryRepository entries;

    private HttpTestClient http;
    private String ownerToken;
    private String intruderToken;
    private long first;
    private long second;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(eq(GamesTestData.HADES_ID))).thenReturn(List.of(GamesTestData.hades()));

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        first = track(ownerToken, GamesTestData.BOTW_ID);
        second = track(ownerToken, GamesTestData.HADES_ID);
    }

    @Test
    void aFavouriteHasNoRankUntilTheReaderArrangesThem() {
        assertThat(entries.findByIdAndUserId(first, ownerId()).orElseThrow().getFavoriteRank())
                .isNull();
    }

    @Test
    void theArrangementIsStoredAsThePositionInTheList() {
        Response response = reorder(ownerToken, List.of(second, first));

        assertThat(response.status()).isEqualTo(200);
        assertThat(rankOf(second)).isZero();
        assertThat(rankOf(first)).isEqualTo(1);
    }

    /** Dragging the same card back is the same write again, not a second arrangement. */
    @Test
    void arrangingTwiceLeavesTheSecondArrangement() {
        reorder(ownerToken, List.of(second, first));
        reorder(ownerToken, List.of(first, second));

        assertThat(rankOf(first)).isZero();
        assertThat(rankOf(second)).isEqualTo(1);
    }

    @Test
    void theRankComesBackWithTheEntry() {
        reorder(ownerToken, List.of(second, first));

        Map<String, Object> entry = http.get("/entries/" + second, "Authorization", "Bearer " + ownerToken)
                .body();

        assertThat(entry).containsEntry("favoriteRank", 0);
    }

    /**
     * An id nobody else owns is not merely skipped: the whole arrangement is refused, since
     * writing the rest would leave a shelf in an order the reader never asked for.
     */
    @Test
    void anotherUsersEntryCannotBeArrangedIntoYourFavourites() {
        long intrudersEntry = track(intruderToken, GamesTestData.BOTW_ID);

        assertThat(reorder(intruderToken, List.of(intrudersEntry, first)).status())
                .isEqualTo(404);
        assertThat(rankOf(first)).isNull();
        assertThat(rankOf(intrudersEntry)).isNull();
    }

    private Response reorder(String token, List<Long> entryIds) {
        return http.putJson("/entries/favourites/order", Map.of("entryIds", entryIds), "Authorization", "Bearer " + token);
    }

    private long track(String token, String externalId) {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", externalId, "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);
        return ((Number) response.body().get("id")).longValue();
    }

    private Integer rankOf(long entryId) {
        return entries.findById(entryId).orElseThrow().getFavoriteRank();
    }

    private Long ownerId() {
        return entries.findById(first).orElseThrow().getUserId();
    }
}
