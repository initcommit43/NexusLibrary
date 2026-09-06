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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** The character a reader stands behind, and whose character it stays. */
class ProfilePictureIntegrationTest extends PostgresIntegrationTest {

    private static final String ONE_PIECE = "21";
    private static final String NARUTO = "20";

    /** AniList's own ids for three characters, the last of them portrait-less. */
    private static final String LUFFY = "40";
    private static final String NAMI = "62";
    private static final String FACELESS = "77";

    @LocalServerPort
    int port;

    @MockitoBean
    AniListClient anilistClient;

    @MockitoBean
    IgdbClient igdbClient;

    private HttpTestClient http;
    private String ownerToken;
    private String intruderToken;
    private long onePiece;
    private long naruto;
    private long aGame;

    @BeforeEach
    void setUp() {
        resetDatabase();

        http = new HttpTestClient(port);

        when(anilistClient.findMediaById(eq(ONE_PIECE))).thenReturn(List.of(anime(21, "One Piece")));
        when(anilistClient.findMediaById(eq(NARUTO))).thenReturn(List.of(anime(20, "Naruto")));
        when(anilistClient.findMediaDetail(eq(ONE_PIECE)))
                .thenReturn(withCharacters(anime(21, "One Piece")));
        when(anilistClient.findMediaDetail(eq(NARUTO)))
                .thenReturn(withCharacters(anime(20, "Naruto")));

        // A source with no characters at all, which is the case the reader has to be told about.
        when(igdbClient.findGameById(eq(GamesTestData.BOTW_ID))).thenReturn(List.of(GamesTestData.botw()));
        when(igdbClient.findGameDetail(eq(GamesTestData.BOTW_ID)))
                .thenReturn(Optional.of(GamesTestData.botw()));

        ownerToken = registerAndGetToken(http, "owner@example.com", "owner");
        intruderToken = registerAndGetToken(http, "intruder@example.com", "intruder");

        onePiece = track(ownerToken, "ANILIST", ONE_PIECE);
        naruto = track(ownerToken, "ANILIST", NARUTO);
        aGame = track(ownerToken, "IGDB", GamesTestData.BOTW_ID);
    }

    @Test
    void thereIsNoPictureUntilTheReaderChoosesOne() {
        Response response = http.get("/settings/profile-picture", "Authorization", "Bearer " + ownerToken);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.rawBody()).isBlank();
    }

    /** The url is read out of the title's own detail, so no request can name an image itself. */
    @Test
    void thePictureIsThePortraitOfTheChosenCharacter() {
        Response response = choose(ownerToken, onePiece, LUFFY);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body())
                .containsEntry("imageUrl", "https://anilist.test/character/40.jpg")
                .containsEntry("characterName", "Character 40")
                .containsEntry("title", "One Piece")
                .containsEntry("mediaType", "ANIME")
                .containsEntry("source", "ANILIST")
                .containsEntry("externalId", ONE_PIECE);
    }

    @Test
    void theChoiceIsThereOnTheNextRead() {
        choose(ownerToken, onePiece, LUFFY);

        assertThat(current(ownerToken)).containsEntry("characterName", "Character 40");
    }

    /** One picture, not a history of them: choosing again moves the same row. */
    @Test
    void choosingAgainReplacesThePicture() {
        choose(ownerToken, onePiece, LUFFY);

        assertThat(choose(ownerToken, naruto, NAMI).status()).isEqualTo(200);
        assertThat(current(ownerToken))
                .containsEntry("title", "Naruto")
                .containsEntry("characterName", "Character 62");
    }

    @Test
    void aCharacterTheTitleDoesNotHaveIsRefused() {
        Response response = choose(ownerToken, onePiece, "999");

        assertThat(response.status()).isEqualTo(409);
        assertThat((String) response.body().get("message")).contains("One Piece");
    }

    /** A character without a portrait cannot stand as a picture, so it is not stored as one. */
    @Test
    void aCharacterWithNoPortraitIsRefusedRatherThanStoredEmpty() {
        assertThat(choose(ownerToken, onePiece, FACELESS).status()).isEqualTo(409);
    }

    /** Only AniList carries characters; every other source simply has none to give. */
    @Test
    void aTitleFromASourceWithNoCharactersIsRefused() {
        Response response = choose(ownerToken, aGame, LUFFY);

        assertThat(response.status()).isEqualTo(409);
        assertThat((String) response.body().get("message"))
                .contains("The Legend of Zelda: Breath of the Wild");
    }

    /** The only entry id in the request is the reader's own, and another's is not found. */
    @Test
    void anotherReadersEntryIsNotAPictureToChoose() {
        assertThat(choose(intruderToken, onePiece, LUFFY).status()).isEqualTo(404);
        assertThat(http.get("/settings/profile-picture", "Authorization", "Bearer " + intruderToken)
                        .rawBody())
                .isBlank();
    }

    @Test
    void removingThePictureLeavesThePlainIcon() {
        choose(ownerToken, onePiece, LUFFY);

        assertThat(http.delete("/settings/profile-picture", "Authorization", "Bearer " + ownerToken)
                        .status())
                .isEqualTo(204);
        assertThat(http.get("/settings/profile-picture", "Authorization", "Bearer " + ownerToken)
                        .rawBody())
                .isBlank();
    }

    @Test
    void aNewPictureStartsAsAPlainCoverCrop() {
        assertThat(choose(ownerToken, onePiece, LUFFY).body())
                .containsEntry("focusX", 50)
                .containsEntry("focusY", 50)
                .containsEntry("zoom", 100);
    }

    @Test
    void theFramingIsRememberedAgainstTheSamePicture() {
        choose(ownerToken, onePiece, LUFFY);

        assertThat(frame(ownerToken, 30, 20, 175).status()).isEqualTo(200);
        assertThat(current(ownerToken))
                .containsEntry("focusX", 30)
                .containsEntry("focusY", 20)
                .containsEntry("zoom", 175);
    }

    /** These go straight into the style the head is drawn with, so they are bounded here. */
    @Test
    void framingOutsideThePictureIsRefused() {
        choose(ownerToken, onePiece, LUFFY);

        assertThat(frame(ownerToken, 30, 140, 100).status()).isEqualTo(400);
        assertThat(frame(ownerToken, 30, 40, 900).status()).isEqualTo(400);
        // Shrinking below the crop that fills the circle would leave a gap around the portrait.
        assertThat(frame(ownerToken, 30, 40, 40).status()).isEqualTo(400);
    }

    /** Offsets into one portrait mean nothing in the next, so a new choice starts square. */
    @Test
    void choosingAnotherCharacterStartsItsFramingAfresh() {
        choose(ownerToken, onePiece, LUFFY);
        frame(ownerToken, 20, 10, 220);

        choose(ownerToken, onePiece, NAMI);

        assertThat(current(ownerToken))
                .containsEntry("focusX", 50)
                .containsEntry("focusY", 50)
                .containsEntry("zoom", 100);
    }

    @Test
    void thereIsNothingToFrameBeforeAPictureIsChosen() {
        assertThat(frame(ownerToken, 30, 20, 150).status()).isEqualTo(404);
    }

    private static Map<String, Object> anime(int id, String title) {
        Map<String, Object> media = new HashMap<>();
        media.put("id", id);
        media.put("type", "ANIME");
        media.put("status", "FINISHED");
        media.put("episodes", 12);
        media.put("title", new HashMap<>(Map.of("english", title)));
        return media;
    }

    /** AniList hangs its characters off edges, and leaves an image empty for some of them. */
    private static Map<String, Object> withCharacters(Map<String, Object> media) {
        Map<String, Object> detailed = new HashMap<>(media);
        detailed.put(
                "characters",
                Map.of("edges", List.of(edge(LUFFY, true), edge(NAMI, true), edge(FACELESS, false))));
        return detailed;
    }

    private static Map<String, Object> edge(String characterId, boolean pictured) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", Integer.parseInt(characterId));
        node.put("name", Map.of("full", "Character " + characterId));
        node.put(
                "image",
                Map.of("medium", pictured ? "https://anilist.test/character/" + characterId + ".jpg" : ""));
        return Map.of("role", "MAIN", "node", node);
    }

    private Response choose(String token, long entryId, String characterId) {
        return http.putJson(
                "/settings/profile-picture",
                Map.of("entryId", entryId, "characterId", characterId),
                "Authorization",
                "Bearer " + token);
    }

    private Response frame(String token, int focusX, int focusY, int zoom) {
        return http.patchJson(
                "/settings/profile-picture",
                Map.of("focusX", focusX, "focusY", focusY, "zoom", zoom),
                "Authorization",
                "Bearer " + token);
    }

    private Map<String, Object> current(String token) {
        Response response = http.get("/settings/profile-picture", "Authorization", "Bearer " + token);
        assertThat(response.status()).isEqualTo(200);
        return response.body();
    }

    private long track(String token, String source, String externalId) {
        Response response = http.postJson(
                "/entries",
                Map.of("source", source, "externalId", externalId, "status", "PLANNING"),
                "Authorization",
                "Bearer " + token);
        return ((Number) response.body().get("id")).longValue();
    }
}
