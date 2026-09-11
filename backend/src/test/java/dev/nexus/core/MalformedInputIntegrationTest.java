package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Input no screen sends, answered as the caller's mistake. Each case here once reached a parse
 * or a copy that threw, which the catch-all answered with a 500 and a stack trace at ERROR —
 * a server fault any signed-in caller could produce at will.
 */
class MalformedInputIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    private HttpTestClient http;
    private String token;

    @BeforeEach
    void setUp() {
        resetDatabase();
        http = new HttpTestClient(port);
        token = registerAndGetToken(http, "reader@example.com", "reader");
    }

    @Test
    void aGameIdThatIsNotANumberIsNotFound() {
        assertThat(get("/catalog/media/IGDB/not-a-number").status()).isEqualTo(404);
    }

    @Test
    void anAniListIdThatIsNotANumberIsNotFound() {
        assertThat(get("/catalog/media/ANILIST/not-a-number").status()).isEqualTo(404);
    }

    /** Digits alone are not enough: AniList's ids are ints, and this one overflows the parse. */
    @Test
    void anAniListIdPastTheRangeOfItsIdsIsNotFound() {
        assertThat(get("/catalog/media/ANILIST/99999999999").status()).isEqualTo(404);
    }

    @Test
    void trackingAnIdTheSourceNeverIssuedIsNotFound() {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", "not-a-number", "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);

        assertThat(response.status()).isEqualTo(404);
    }

    @Test
    void aStudioIdThatIsNotANumberHasNoWorks() {
        assertThat(get("/catalog/studios/ANILIST/not-a-number").status()).isEqualTo(200);
    }

    /** The service copies the list into an EnumSet, which throws on a null rather than skipping it. */
    @Test
    void aNullAmongTheSwitchedOffModulesIsRefused() {
        assertThat(put("/settings/modules", Map.of("disabled", Arrays.asList("BOOK", null))).status())
                .isEqualTo(400);
    }

    @Test
    void aNullAmongTheFavouriteRowsIsRefused() {
        assertThat(put("/settings/favourite-rows", Map.of("order", Arrays.asList("GAME", null), "paired", List.of()))
                        .status())
                .isEqualTo(400);
        assertThat(put("/settings/favourite-rows", Map.of("order", List.of("GAME"), "paired", Arrays.asList("GAME", null)))
                        .status())
                .isEqualTo(400);
    }

    @Test
    void aNullAmongTheFavouritesToReorderIsRefused() {
        assertThat(put("/entries/favourites/order", Map.of("entryIds", Arrays.asList(1, null))).status())
                .isEqualTo(400);
    }

    /** No stored item has a longer id, so asking the source for one would spend a call on nothing. */
    @Test
    void anExternalIdWiderThanTheColumnIsRefusedBeforeTheSourceIsAsked() {
        String tooLong = "1".repeat(65);

        assertThat(get("/catalog/media/IGDB/" + tooLong).status()).isEqualTo(400);
        assertThat(get("/catalog/media/IGDB/" + tooLong + "/achievements").status()).isEqualTo(400);
    }

    @Test
    void aCharacterIdWiderThanTheColumnIsRefused() {
        Response response = put("/settings/profile-picture", Map.of("entryId", 1, "characterId", "1".repeat(65)));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.fieldErrors()).containsKey("characterId");
    }

    private Response get(String path) {
        return http.get(path, "Authorization", "Bearer " + token);
    }

    private Response put(String path, Map<String, ?> body) {
        return http.putJson(path, body, "Authorization", "Bearer " + token);
    }
}
