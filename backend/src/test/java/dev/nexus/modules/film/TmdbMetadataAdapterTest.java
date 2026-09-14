package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TmdbMetadataAdapterTest {

    private TmdbClient client;
    private TmdbMetadataAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(TmdbClient.class);
        adapter = new TmdbMetadataAdapter(
                client, new TmdbProperties(
                        "https://tmdb.test/3", "https://img.tmdb.test/t/p/", "w500", "w1280", "w185", "token", 20));
    }

    @Test
    void servesBothKindsFromOneSource() {
        assertThat(adapter.mediaTypes()).containsExactlyInAnyOrder(MediaType.MOVIE, MediaType.SHOW);
        assertThat(adapter.source()).isEqualTo(Source.TMDB);
    }

    /**
     * The collision this module has to avoid: TMDB numbers films and shows separately, so
     * a bare id would file {@code /tv/550} over {@code /movie/550} under the unique
     * {@code (source, external_id)} — one title silently overwriting an unrelated one.
     */
    @Test
    void idsCarryTheirKindSoTheTwoNumberSpacesCannotCollide() {
        when(client.search(TmdbKind.MOVIE, "550", 1)).thenReturn(List.of(Map.of("id", 550, "title", "Fight Club")));
        when(client.search(TmdbKind.SHOW, "550", 1)).thenReturn(List.of(Map.of("id", 550, "name", "Lost Girl")));

        String movieId = adapter.search(MediaType.MOVIE, "550", 1).getFirst().externalId();
        String showId = adapter.search(MediaType.SHOW, "550", 1).getFirst().externalId();

        assertThat(movieId).isEqualTo("movie:550");
        assertThat(showId).isEqualTo("tv:550");
        assertThat(movieId).isNotEqualTo(showId);
    }

    /** {@code fetchById} is handed an id and no media type, so the id has to route the call. */
    @Test
    void theAdultFlagIsCarriedOverFromTmdb() {
        when(client.findById(TmdbKind.MOVIE, "1"))
                .thenReturn(Optional.of(Map.of("id", 1, "title", "Grown-up film", "adult", true)));
        when(client.findById(TmdbKind.MOVIE, "2")).thenReturn(Optional.of(Map.of("id", 2, "title", "Fight Club")));

        assertThat(adapter.fetchById("movie:1").orElseThrow().adult()).isTrue();
        assertThat(adapter.fetchById("movie:2").orElseThrow().adult()).isFalse();
    }

    @Test
    void fetchByIdRoutesToTheEndpointItsPrefixNames() {
        when(client.findById(TmdbKind.SHOW, "1396"))
                .thenReturn(Optional.of(Map.of("id", 1396, "name", "Breaking Bad", "status", "Ended")));

        Optional<TrackableItemData> item = adapter.fetchById("tv:1396");

        assertThat(item).isPresent();
        assertThat(item.get().mediaType()).isEqualTo(MediaType.SHOW);
        assertThat(item.get().title()).isEqualTo("Breaking Bad");
        verify(client).findById(TmdbKind.SHOW, "1396");
    }

    /** An id from another source, or from before the prefix existed, is simply not ours. */
    @Test
    void anUnprefixedIdIsNotFoundRatherThanAGuess() {
        assertThat(adapter.fetchById("550")).isEmpty();
        verify(client, org.mockito.Mockito.never()).findById(org.mockito.ArgumentMatchers.any(), anyString());
    }

    @Test
    void readsTheTitleFieldEachKindActuallyUses() {
        when(client.findById(TmdbKind.MOVIE, "550"))
                .thenReturn(Optional.of(Map.of("id", 550, "title", "Fight Club", "name", "wrong")));

        assertThat(adapter.fetchById("movie:550").orElseThrow().title()).isEqualTo("Fight Club");
    }

    /**
     * Staleness rests on this: a finished show is settled and never refreshed again, while
     * a returning one keeps gaining episodes and has to be re-read.
     */
    @Test
    void aFinishedShowIsSettledAndAReturningOneIsNot() {
        when(client.findById(TmdbKind.SHOW, "1"))
                .thenReturn(Optional.of(Map.of("id", 1, "name", "Ended Show", "status", "Ended", "first_air_date", "2008-01-20")));
        when(client.findById(TmdbKind.SHOW, "2"))
                .thenReturn(Optional.of(
                        Map.of("id", 2, "name", "Running Show", "status", "Returning Series", "first_air_date", "2019-11-12")));

        assertThat(adapter.fetchById("tv:1").orElseThrow().itemState()).isEqualTo(ItemState.RELEASED);
        assertThat(adapter.fetchById("tv:2").orElseThrow().itemState()).isEqualTo(ItemState.ONGOING);
    }

    @Test
    void anUnairedTitleIsUpcoming() {
        String futureDate = LocalDate.now().plusYears(1).toString();
        when(client.findById(TmdbKind.MOVIE, "9"))
                .thenReturn(Optional.of(Map.of("id", 9, "title", "Not Out Yet", "status", "Planned", "release_date", futureDate)));

        assertThat(adapter.fetchById("movie:9").orElseThrow().itemState()).isEqualTo(ItemState.UPCOMING);
    }

    /** TMDB writes an unknown date as an empty string, which {@code LocalDate.parse} rejects. */
    @Test
    void anEmptyDateIsAbsentRatherThanAnError() {
        when(client.findById(TmdbKind.SHOW, "3"))
                .thenReturn(Optional.of(Map.of("id", 3, "name", "Undated", "first_air_date", "")));

        TrackableItemData item = adapter.fetchById("tv:3").orElseThrow();

        assertThat(item.releaseDate()).isNull();
        assertThat(item.itemState()).isEqualTo(ItemState.UPCOMING);
    }

    @Test
    void buildsPosterUrlsAtTheConfiguredWidth() {
        when(client.findById(TmdbKind.MOVIE, "550"))
                .thenReturn(Optional.of(Map.of("id", 550, "title", "Fight Club", "poster_path", "/abc.jpg")));

        assertThat(adapter.fetchById("movie:550").orElseThrow().coverUrl())
                .isEqualTo("https://img.tmdb.test/t/p/w500/abc.jpg");
    }

    /** TMDB rates on 0-10 and core stores 0-100, so the conversion happens once, here. */
    @Test
    void scalesTheExternalRatingToCoreScale() {
        when(client.findById(TmdbKind.MOVIE, "550"))
                .thenReturn(Optional.of(Map.of("id", 550, "title", "Fight Club", "vote_average", 8.44)));

        assertThat(adapter.fetchById("movie:550").orElseThrow().metadata()).containsEntry("externalRating", 84L);
    }

    @Test
    void carriesTheFieldsTheLibraryFiltersOn() {
        when(client.findById(TmdbKind.SHOW, "1396"))
                .thenReturn(Optional.of(Map.of(
                        "id", 1396,
                        "name", "Breaking Bad",
                        "type", "Scripted",
                        "overview", "A chemistry teacher.",
                        "genres", List.of(Map.of("id", 18, "name", "Drama")),
                        "number_of_episodes", 62,
                        "number_of_seasons", 5)));

        Map<String, Object> metadata = adapter.fetchById("tv:1396").orElseThrow().metadata();

        assertThat(metadata).containsEntry("format", "Scripted");
        assertThat(metadata).containsEntry("summary", "A chemistry teacher.");
        assertThat(metadata).containsEntry("genres", List.of("Drama"));
        assertThat(metadata).containsEntry("episodes", 62);
        assertThat(metadata).containsEntry("seasons", 5);
    }

    /** Specials are season 0, and a reader's episode progress does not count them. */
    @Test
    void storesEpisodesPerSeasonInOrderWithoutSpecials() {
        when(client.findById(TmdbKind.SHOW, "1396"))
                .thenReturn(Optional.of(Map.of(
                        "id", 1396,
                        "name", "Breaking Bad",
                        "seasons", List.of(
                                Map.of("season_number", 2, "episode_count", 13),
                                Map.of("season_number", 0, "episode_count", 9),
                                Map.of("season_number", 1, "episode_count", 7)))));

        assertThat(adapter.fetchById("tv:1396").orElseThrow().metadata())
                .containsEntry("seasonEpisodes", List.of(7, 13));
    }

    @Test
    void aShowCachedWithoutTheSeasonBreakdownIsOutdated() {
        TrackableItem old = new TrackableItem(
                MediaType.SHOW, Source.TMDB, "tv:1", "Old", null, null, ItemState.RELEASED, false, Map.of("seasons", 3));
        TrackableItem current = new TrackableItem(
                MediaType.SHOW, Source.TMDB, "tv:2", "New", null, null, ItemState.RELEASED, false,
                Map.of("seasonEpisodes", List.of()));
        TrackableItem film = new TrackableItem(
                MediaType.MOVIE, Source.TMDB, "movie:3", "Film", null, null, ItemState.RELEASED, false, Map.of());

        assertThat(adapter.isOutdated(old)).isTrue();
        assertThat(adapter.isOutdated(current)).isFalse();
        assertThat(adapter.isOutdated(film)).isFalse();
    }

    /** Written even when TMDB lists no seasons, so the show is not taken for an old copy again. */
    @Test
    void aShowWithNoSeasonsStillRecordsAnEmptyBreakdown() {
        when(client.findById(TmdbKind.SHOW, "1")).thenReturn(Optional.of(Map.of("id", 1, "name", "Pilot")));

        assertThat(adapter.fetchById("tv:1").orElseThrow().metadata()).containsEntry("seasonEpisodes", List.of());
    }

    /** TMDB gives a date only; the episode counts as out from 06:00 UTC that day. */
    @Test
    void storesTheNextEpisodeWithItsSeasonAiringOnTheMorningOfItsDate() {
        when(client.findById(TmdbKind.SHOW, "1396"))
                .thenReturn(Optional.of(Map.of(
                        "id", 1396,
                        "name", "Breaking Bad",
                        "next_episode_to_air",
                        Map.of("season_number", 2, "episode_number", 5, "air_date", "2026-10-01"))));

        assertThat(adapter.fetchById("tv:1396").orElseThrow().metadata())
                .containsEntry(
                        "nextSeasonEpisode",
                        Map.of("season", 2, "episode", 5, "airingAt",
                                java.time.Instant.parse("2026-10-01T06:00:00Z").getEpochSecond()));
    }

    /** Written as null, so a refresh clears an episode that has aired instead of keeping it. */
    @Test
    void aShowWithNothingScheduledRecordsNoNextEpisode() {
        when(client.findById(TmdbKind.SHOW, "1")).thenReturn(Optional.of(Map.of("id", 1, "name", "Pilot")));

        Map<String, Object> metadata = adapter.fetchById("tv:1").orElseThrow().metadata();
        assertThat(metadata).containsKey("nextSeasonEpisode");
        assertThat(metadata.get("nextSeasonEpisode")).isNull();
    }

    @Test
    void aShowWhoseNextEpisodeHasAiredIsOutdated() {
        long past = java.time.Instant.now().minusSeconds(60).getEpochSecond();
        long future = java.time.Instant.now().plusSeconds(3600).getEpochSecond();
        TrackableItem aired = new TrackableItem(
                MediaType.SHOW, Source.TMDB, "tv:1", "Aired", null, null, ItemState.ONGOING, false,
                Map.of("seasonEpisodes", List.of(8),
                        "nextSeasonEpisode", Map.of("season", 1, "episode", 3, "airingAt", past)));
        TrackableItem waiting = new TrackableItem(
                MediaType.SHOW, Source.TMDB, "tv:2", "Waiting", null, null, ItemState.ONGOING, false,
                Map.of("seasonEpisodes", List.of(8),
                        "nextSeasonEpisode", Map.of("season", 1, "episode", 3, "airingAt", future)));

        assertThat(adapter.isOutdated(aired)).isTrue();
        assertThat(adapter.isOutdated(waiting)).isFalse();
    }

    @Test
    void searchAsksForTheKindThatMatchesTheMediaType() {
        when(client.search(TmdbKind.SHOW, "bad", 5)).thenReturn(List.of());

        adapter.search(MediaType.SHOW, "bad", 5);

        verify(client).search(TmdbKind.SHOW, "bad", 5);
        verify(client, org.mockito.Mockito.never()).search(org.mockito.ArgumentMatchers.eq(TmdbKind.MOVIE), anyString(), anyInt());
    }
}
