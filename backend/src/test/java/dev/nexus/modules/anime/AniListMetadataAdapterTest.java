package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
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
        when(client.searchMedia(any(), anyString(), anyInt())).thenReturn(List.of(media()));

        var results = adapter.search(MediaType.ANIME, "one piece", 10);

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

}
