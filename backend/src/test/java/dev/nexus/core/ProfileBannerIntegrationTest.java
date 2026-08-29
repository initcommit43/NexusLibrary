package dev.nexus.core;

import static dev.nexus.support.AuthenticatedTest.registerAndGetToken;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import dev.nexus.modules.games.IgdbClient;
import dev.nexus.support.GamesTestData;
import dev.nexus.support.HttpTestClient;
import dev.nexus.support.HttpTestClient.Response;
import dev.nexus.support.PostgresIntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The picture a reader puts at the head of their profile, and whose picture it stays. */
class ProfileBannerIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    int port;

    @MockitoBean
    IgdbClient igdbClient;

    private HttpTestClient http;
    private String ownerToken;
    private String intruderToken;
    private long withArt;
    private long withoutArt;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameById(eq(GamesTestData.HADES_ID))).thenReturn(List.of(GamesTestData.hades()));
        when(igdbClient.findGameDetail(eq(GamesTestData.BOTW_ID)))
                .thenReturn(Optional.of(withScreenshots(GamesTestData.botw(), "shot-one", "shot-two")));
        // A title its source has no wide art for, which is the case the reader has to be told about.
        when(igdbClient.findGameDetail(eq(GamesTestData.HADES_ID)))
                .thenReturn(Optional.of(GamesTestData.hades()));

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        withArt = track(ownerToken, GamesTestData.BOTW_ID);
        withoutArt = track(ownerToken, GamesTestData.HADES_ID);
    }

    @Test
    void thereIsNoBannerUntilTheReaderChoosesOne() {
        Response response = http.get("/settings/profile-banner", "Authorization", "Bearer " + ownerToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rawBody()).isBlank();
    }

    /** The url is read out of the title's own detail, so no request can name an image itself. */
    @Test
    void theBannerIsTheWideArtOfTheTitleBehindIt() {
        Response response = choose(ownerToken, withArt);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body())
                .containsEntry("imageUrl", "https://images.igdb.com/igdb/image/upload/t_screenshot_big/shot-one.jpg")
                .containsEntry("title", "The Legend of Zelda: Breath of the Wild")
                .containsEntry("mediaType", "GAME")
                .containsEntry("source", "IGDB")
                .containsEntry("externalId", GamesTestData.BOTW_ID);
    }

    @Test
    void theChoiceIsThereOnTheNextRead() {
        choose(ownerToken, withArt);

        assertThat(current(ownerToken)).containsEntry("title", "The Legend of Zelda: Breath of the Wild");
    }

    /** One banner, not a history of them: choosing again moves the same row. */
    @Test
    void choosingAgainReplacesTheBanner() {
        choose(ownerToken, withArt);
        when(igdbClient.findGameDetail(eq(GamesTestData.HADES_ID)))
                .thenReturn(Optional.of(withScreenshots(GamesTestData.hades(), "hades-shot")));

        assertThat(choose(ownerToken, withoutArt).status()).isEqualTo(200);
        assertThat(current(ownerToken)).containsEntry("title", "Hades");
    }

    @Test
    void aTitleWithNoWideArtIsRefusedRatherThanStoredEmpty() {
        Response response = choose(ownerToken, withoutArt);

        assertThat(response.status()).isEqualTo(409);
        assertThat((String) response.body().get("message")).contains("Hades");
    }

    /** The only id in the request is an entry's, and an entry that is not yours is not found. */
    @Test
    void anotherReadersEntryIsNotABannerToChoose() {
        assertThat(choose(intruderToken, withArt).status()).isEqualTo(404);
        assertThat(http.get("/settings/profile-banner", "Authorization", "Bearer " + intruderToken)
                        .rawBody())
                .isBlank();
    }

    @Test
    void removingTheBannerLeavesTheHeadBare() {
        choose(ownerToken, withArt);

        assertThat(http.delete("/settings/profile-banner", "Authorization", "Bearer " + ownerToken)
                        .status())
                .isEqualTo(204);
        assertThat(http.get("/settings/profile-banner", "Authorization", "Bearer " + ownerToken)
                        .rawBody())
                .isBlank();
    }

    @Test
    void aNewBannerStartsAsAPlainCoverCrop() {
        assertThat(choose(ownerToken, withArt).body())
                .containsEntry("focusX", 50)
                .containsEntry("focusY", 50)
                .containsEntry("zoom", 100);
    }

    @Test
    void theFramingIsRememberedAgainstTheSamePicture() {
        choose(ownerToken, withArt);

        assertThat(frame(ownerToken, 30, 80, 175).status()).isEqualTo(200);
        assertThat(current(ownerToken))
                .containsEntry("focusX", 30)
                .containsEntry("focusY", 80)
                .containsEntry("zoom", 175);
    }

    /** These go straight into the style the head is drawn with, so they are bounded here. */
    @Test
    void framingOutsideThePictureIsRefused() {
        choose(ownerToken, withArt);

        assertThat(frame(ownerToken, 30, 140, 100).status()).isEqualTo(400);
        assertThat(frame(ownerToken, 30, 40, 900).status()).isEqualTo(400);
        // Shrinking below the crop that fills the strip would leave the head part empty.
        assertThat(frame(ownerToken, 30, 40, 40).status()).isEqualTo(400);
    }

    /** Offsets into one picture mean nothing in the next, so a new choice starts square. */
    @Test
    void choosingAnotherPictureStartsItsFramingAfresh() {
        choose(ownerToken, withArt);
        frame(ownerToken, 20, 90, 220);

        when(igdbClient.findGameDetail(eq(GamesTestData.HADES_ID)))
                .thenReturn(Optional.of(withScreenshots(GamesTestData.hades(), "hades-shot")));
        choose(ownerToken, withoutArt);

        assertThat(current(ownerToken))
                .containsEntry("focusX", 50)
                .containsEntry("focusY", 50)
                .containsEntry("zoom", 100);
    }

    @Test
    void thereIsNothingToFrameBeforeABannerIsChosen() {
        assertThat(frame(ownerToken, 30, 80, 150).status()).isEqualTo(404);
    }

    private static Map<String, Object> withScreenshots(Map<String, Object> game, String... imageIds) {
        Map<String, Object> detailed = new HashMap<>(game);
        detailed.put(
                "screenshots",
                java.util.Arrays.stream(imageIds)
                        .map(id -> Map.<String, Object>of("image_id", id))
                        .toList());
        return detailed;
    }

    private Response choose(String token, long entryId) {
        return http.putJson(
                "/settings/profile-banner",
                Map.of("entryId", entryId),
                "Authorization",
                "Bearer " + token);
    }

    private Response frame(String token, int focusX, int focusY, int zoom) {
        return http.patchJson(
                "/settings/profile-banner",
                Map.of("focusX", focusX, "focusY", focusY, "zoom", zoom),
                "Authorization",
                "Bearer " + token);
    }

    private Map<String, Object> current(String token) {
        Response response = http.get("/settings/profile-banner", "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    private long track(String token, String externalId) {
        Response response = http.postJson(
                "/entries",
                Map.of("source", "IGDB", "externalId", externalId, "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);
        return ((Number) response.body().get("id")).longValue();
    }
}
