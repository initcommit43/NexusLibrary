package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Maps an AniList media record onto the shared item shape. */
class AniListMetadataAdapterTest {

    private AniListClient client;
    private AniListMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(AniListClient.class);
        adapter = new AniListMetadataAdapter(client);
    }

    @Test
    void oneAdapterCoversBothAnimeAndManga() {
        assertThat(adapter.mediaTypes()).containsExactlyInAnyOrder(MediaType.ANIME, MediaType.MANGA);
        assertThat(adapter.source()).isEqualTo(Source.ANILIST);
    }

    /** AniList's ids are ints, so a word or a number past one is not something it has. */
    @Test
    void anIdAniListCouldNotHaveIssuedFindsNothingWithoutAskingAniList() {
        assertThat(adapter.fetchById("not-a-number")).isEmpty();
        assertThat(adapter.fetchById("99999999999")).isEmpty();
        assertThat(adapter.fetchDetail("not-a-number")).isEmpty();
        assertThat(adapter.worksOf("not-a-number", 1, 20).items()).isEmpty();

        verifyNoInteractions(client);
    }

    @Test
    void aMediaRecordBecomesATrackableItem() {
        TrackableItemData data = adapter.toItemData(media());

        assertThat(data.mediaType()).isEqualTo(MediaType.ANIME);
        assertThat(data.source()).isEqualTo(Source.ANILIST);
        assertThat(data.externalId()).isEqualTo("21");
        assertThat(data.title()).isEqualTo("One Piece");
        assertThat(data.coverUrl()).isEqualTo("https://anilist.test/cover.jpg");
        assertThat(data.releaseDate()).isEqualTo(LocalDate.of(1999, 10, 20));
        assertThat(data.itemState()).isEqualTo(ItemState.ONGOING);
        assertThat(data.metadata())
                .containsEntry("malId", 21)
                .containsEntry("externalRating", 88)
                .containsEntry("episodes", 1000)
                .containsEntry("studios", List.of("Toei Animation"));
    }

    /** The type comes off the record, so a manga never lands on the anime shelf. */
    @Test
    void theAdultFlagIsCarriedOverFromAniList() {
        assertThat(adapter.toItemData(media()).adult()).isFalse();

        Map<String, Object> adult = media();
        adult.put("isAdult", true);
        assertThat(adapter.toItemData(adult).adult()).isTrue();
    }

    @Test
    void adultRelationsAreLeftOutOfACopyAndTheCachedDetailIsUntouched() {
        Map<String, Object> detail = Map.of("relations", Map.of("edges", List.of(
                Map.of("relationType", "SEQUEL", "node", Map.of("id", 1, "isAdult", false)),
                Map.of("relationType", "SIDE_STORY", "node", Map.of("id", 2, "isAdult", true)))));

        Map<String, Object> hidden = adapter.withoutAdult(detail);

        assertThat(hidden).isNotSameAs(detail);
        assertThat((Map<?, ?>) hidden.get("relations")).extracting(r -> ((List<?>) r.get("edges")).size()).isEqualTo(1);
        assertThat(((List<?>) ((Map<?, ?>) detail.get("relations")).get("edges"))).hasSize(2);
        assertThat(adapter.withoutAdult(Map.of("title", "x"))).containsOnlyKeys("title");
    }

    /** A recommendation points at another title as a relation does, and goes the same way. */
    @Test
    void adultRecommendationsAreLeftOutOfACopyToo() {
        Map<String, Object> detail = Map.of("recommendations", Map.of("nodes", List.of(
                Map.of("rating", 10, "mediaRecommendation", Map.of("id", 1, "isAdult", false)),
                Map.of("rating", 5, "mediaRecommendation", Map.of("id", 2, "isAdult", true)))));

        Map<String, Object> hidden = adapter.withoutAdult(detail);

        assertThat((Map<?, ?>) hidden.get("recommendations"))
                .extracting(r -> ((List<?>) r.get("nodes")).size())
                .isEqualTo(1);
        assertThat(((List<?>) ((Map<?, ?>) detail.get("recommendations")).get("nodes"))).hasSize(2);
    }

    @Test
    void aDetailWithNothingAdultIsHandedBackAsItWas() {
        Map<String, Object> detail = Map.of("recommendations", Map.of("nodes", List.of(
                Map.of("rating", 10, "mediaRecommendation", Map.of("id", 1, "isAdult", false)))));

        assertThat(adapter.withoutAdult(detail)).isSameAs(detail);
    }

    @Test
    void mangaIsRecognisedFromTheRecordRatherThanTheQuery() {
        Map<String, Object> manga = media();
        manga.put("type", "MANGA");

        assertThat(adapter.toItemData(manga).mediaType()).isEqualTo(MediaType.MANGA);
    }

    /** Plenty of titles have no English entry, and native script is unsearchable for most. */
    @Test
    void titleFallsBackFromEnglishToRomajiBeforeNative() {
        Map<String, Object> withoutEnglish = media();
        withoutEnglish.put("title", new HashMap<>(Map.of("romaji", "Boku no Hero", "native", "僕のヒーロー")));

        assertThat(adapter.toItemData(withoutEnglish).title()).isEqualTo("Boku no Hero");
    }

    @Test
    void nativeTitleIsUsedWhenItIsAllThereIs() {
        Map<String, Object> nativeOnly = media();
        nativeOnly.put("title", new HashMap<>(Map.of("native", "僕のヒーロー")));

        assertThat(adapter.toItemData(nativeOnly).title()).isEqualTo("僕のヒーロー");
    }

    /**
     * An announced title often carries only a year. Filling the gaps would invent a release
     * day, and the date is what the UI shows.
     */
    @Test
    void aPartialStartDateIsLeftEmptyRatherThanGuessed() {
        Map<String, Object> announced = media();
        announced.put("startDate", new HashMap<>(Map.of("year", 2026)));

        assertThat(adapter.toItemData(announced).releaseDate()).isNull();
    }

    @Test
    void publishingStatusDecidesHowOftenTheItemIsRefreshed() {
        assertThat(stateFor("FINISHED")).isEqualTo(ItemState.RELEASED);
        assertThat(stateFor("RELEASING")).isEqualTo(ItemState.ONGOING);
        assertThat(stateFor("HIATUS")).isEqualTo(ItemState.ONGOING);
        assertThat(stateFor("NOT_YET_RELEASED")).isEqualTo(ItemState.UPCOMING);
        // A cancelled series gains nothing from here, so it is as settled as a finished one.
        assertThat(stateFor("CANCELLED")).isEqualTo(ItemState.RELEASED);
    }

    @Test
    void aSearchAsksForTheTypeTheCallerWants() {
        when(client.searchMedia(any(), anyString(), anyInt(), anyBoolean())).thenReturn(List.of(media()));

        var results = adapter.search(MediaType.ANIME, "one piece", 10, false);

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.title()).isEqualTo("One Piece");
            assertThat(result.externalId()).isEqualTo("21");
        });
    }

    @Test
    void aMalIdResolvesOntoTheAniListCanonical() {
        when(client.findMediaByMalId(MediaType.ANIME, "21")).thenReturn(List.of(media()));

        assertThat(adapter.fetchByMalId(MediaType.ANIME, "21"))
                .get()
                .extracting(TrackableItemData::externalId)
                .isEqualTo("21");
    }

    @Test
    void anIdAniListDoesNotHaveIsAnEmptyResult() {
        when(client.findMediaById(anyString())).thenReturn(List.of());

        assertThat(adapter.fetchById("999999")).isEmpty();
    }

    /**
     * A page listing what someone is part-way through wants the countdown beside every title,
     * and the detail query is one request per row. It rides the list fields instead.
     */
    @Test
    @SuppressWarnings("unchecked")
    void theNextEpisodeRidesTheFieldsEveryListReadShares() {
        Map<String, Object> media = media();
        media.put("nextAiringEpisode", Map.of("episode", 1142, "airingAt", 1_756_000_000L));

        Map<String, Object> next =
                (Map<String, Object>) adapter.toItemData(media).metadata().get("nextEpisode");

        assertThat(next).containsEntry("episode", 1142);
        assertThat(next.get("airingAt")).isEqualTo(1_756_000_000L);
    }

    /** A finished series has no next episode, and an absent one is not a countdown of zero. */
    @Test
    void aTitleWithNothingLeftToAirCarriesNoCountdown() {
        assertThat(adapter.toItemData(media()).metadata()).doesNotContainKey("nextEpisode");
    }

    private ItemState stateFor(String status) {
        Map<String, Object> media = media();
        media.put("status", status);
        return adapter.toItemData(media).itemState();
    }

    private static Map<String, Object> media() {
        Map<String, Object> media = new HashMap<>();
        media.put("id", 21);
        media.put("idMal", 21);
        media.put("type", "ANIME");
        media.put("format", "TV");
        media.put("status", "RELEASING");
        media.put("episodes", 1000);
        media.put("averageScore", 88);
        media.put("description", "A pirate crew looks for a treasure.");
        media.put("title", new HashMap<>(Map.of("english", "One Piece", "romaji", "One Piece", "native", "ワンピース")));
        media.put("coverImage", Map.of("large", "https://anilist.test/cover.jpg"));
        media.put("startDate", new HashMap<>(Map.of("year", 1999, "month", 10, "day", 20)));
        media.put("genres", List.of("Action", "Adventure"));
        media.put("studios", Map.of("nodes", List.of(Map.of("name", "Toei Animation"))));
        return media;
    }
    /** AniList names its own banner, and leaves the field out for a title that has none. */
    @Test
    void aBannerIsReadStraightOffTheDetail() {
        assertThat(adapter.bannerFrom(Map.of("bannerImage", "https://anilist.co/banner/21.jpg")))
                .contains("https://anilist.co/banner/21.jpg");
    }

    @Test
    void aTitleWithNoBannerHasNone() {
        assertThat(adapter.bannerFrom(Map.of("title", Map.of("romaji", "One Piece")))).isEmpty();
    }

    /** AniList numbers its characters, and the detail carries them as edges around a node. */
    private Map<String, Object> detailWithCharacters() {
        return Map.of(
                "characters",
                Map.of(
                        "edges",
                        List.of(
                                Map.of(
                                        "role",
                                        "MAIN",
                                        "node",
                                        Map.of(
                                                "id",
                                                40,
                                                "name",
                                                Map.of("full", "Monkey D. Luffy"),
                                                "image",
                                                Map.of("medium", "https://anilist.co/character/40.jpg"))),
                                Map.of(
                                        "role",
                                        "SUPPORTING",
                                        "node",
                                        Map.of(
                                                "id",
                                                62,
                                                "name",
                                                Map.of("full", "Nami"),
                                                "image",
                                                Map.of("medium", ""))))));
    }

    @Test
    void aCharacterIsFoundByItsAniListId() {
        assertThat(adapter.characterFrom(detailWithCharacters(), "40"))
                .get()
                .satisfies(portrait -> {
                    assertThat(portrait.id()).isEqualTo("40");
                    assertThat(portrait.name()).isEqualTo("Monkey D. Luffy");
                    assertThat(portrait.imageUrl()).isEqualTo("https://anilist.co/character/40.jpg");
                });
    }

    @Test
    void aCharacterFromAnotherTitleIsNotFound() {
        assertThat(adapter.characterFrom(detailWithCharacters(), "999")).isEmpty();
    }

    /** A character with no portrait cannot stand as a picture, so it is not offered as one. */
    @Test
    void aCharacterWithNoPortraitIsNotOffered() {
        assertThat(adapter.characterFrom(detailWithCharacters(), "62")).isEmpty();
    }

    @Test
    void aDetailWithNoCharactersAtAllHasNone() {
        assertThat(adapter.characterFrom(Map.of("title", Map.of("romaji", "One Piece")), "40")).isEmpty();
    }

}
