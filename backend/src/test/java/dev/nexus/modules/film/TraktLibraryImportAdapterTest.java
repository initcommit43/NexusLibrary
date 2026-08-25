package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins how six endpoints that each say one thing become one shelf.
 *
 * <p>Trakt has no status field, so every status here is inferred. These tests are where the
 * inferences are written down, because a wrong one is invisible: it produces a plausible
 * library that quietly misstates what someone has watched.
 */
class TraktLibraryImportAdapterTest {

    private static final ExternalAccount ACCOUNT = new ExternalAccount(1L, Provider.TRAKT, "reader");

    private TraktClient client;
    private TraktLibraryImportAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(TraktClient.class);
        when(client.watchedMovies(ACCOUNT)).thenReturn(List.of());
        when(client.watchedShows(ACCOUNT)).thenReturn(List.of());
        when(client.watchlistMovies(ACCOUNT)).thenReturn(List.of());
        when(client.watchlistShows(ACCOUNT)).thenReturn(List.of());
        when(client.ratedMovies(ACCOUNT)).thenReturn(List.of());
        when(client.ratedShows(ACCOUNT)).thenReturn(List.of());
        adapter = new TraktLibraryImportAdapter(client);
    }

    private Map<String, Object> ids(int trakt, int tmdb) {
        return Map.of("trakt", trakt, "tmdb", tmdb);
    }

    private Map<String, Object> movie(int trakt, int tmdb, String title) {
        return Map.of("title", title, "ids", ids(trakt, tmdb));
    }

    private Map<String, Object> show(int trakt, int tmdb, String title, int airedEpisodes) {
        return Map.of("title", title, "ids", ids(trakt, tmdb), "aired_episodes", airedEpisodes);
    }

    /** One episode watched, as Trakt reports a season: a list of the episodes seen. */
    private Map<String, Object> season(int number, int episodesWatched) {
        return Map.of(
                "number",
                number,
                "episodes",
                java.util.Collections.nCopies(episodesWatched, Map.of("number", 1)));
    }

    @Test
    void aWatchedFilmIsCompleted() {
        when(client.watchedMovies(ACCOUNT))
                .thenReturn(List.of(Map.of(
                        "plays", 1, "last_watched_at", "2019-04-26T18:30:00.000Z", "movie", movie(1, 550, "Fight Club"))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.status()).isEqualTo(TrackingStatus.COMPLETED);
        assertThat(entry.finishedAt()).isEqualTo(LocalDate.of(2019, 4, 26));
        assertThat(entry.itemRef().title()).isEqualTo("Fight Club");
    }

    /** The TMDB id is what makes this import need no matching at all. */
    @Test
    void carriesTheTmdbIdAndKindAsHints() {
        when(client.watchedMovies(ACCOUNT))
                .thenReturn(List.of(Map.of("movie", movie(1, 550, "Fight Club"))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.TRAKT);
        assertThat(entry.itemRef().hints())
                .containsEntry(TraktToTmdbResolver.TMDB_ID_HINT, "550")
                .containsEntry(TraktToTmdbResolver.KIND_HINT, "movie");
    }

    @Test
    void aShowWithEveryAiredEpisodeWatchedIsCompleted() {
        when(client.watchedShows(ACCOUNT))
                .thenReturn(List.of(Map.of(
                        "last_watched_at",
                        "2013-09-30T05:00:00.000Z",
                        "show",
                        show(2, 1396, "Breaking Bad", 62),
                        "seasons",
                        List.of(season(1, 7), season(2, 13), season(3, 13), season(4, 13), season(5, 16)))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.status()).isEqualTo(TrackingStatus.COMPLETED);
        assertThat(entry.progressCurrent()).isEqualTo(62);
        assertThat(entry.progressMax()).isEqualTo(62);
        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.EPISODES);
        assertThat(entry.finishedAt()).isEqualTo(LocalDate.of(2013, 9, 30));
    }

    @Test
    void aShowPartWatchedIsInProgressWithNoFinishDate() {
        when(client.watchedShows(ACCOUNT))
                .thenReturn(List.of(Map.of(
                        "last_watched_at",
                        "2013-09-30T05:00:00.000Z",
                        "show",
                        show(2, 1396, "Breaking Bad", 62),
                        "seasons",
                        List.of(season(1, 7)))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.status()).isEqualTo(TrackingStatus.IN_PROGRESS);
        assertThat(entry.progressCurrent()).isEqualTo(7);
        assertThat(entry.finishedAt()).isNull();
    }

    /**
     * Specials are not counted in {@code aired_episodes}, so counting them watched would
     * carry a viewer past the end of a series they have not finished.
     */
    @Test
    void specialsDoNotCountTowardsFinishingAShow() {
        when(client.watchedShows(ACCOUNT))
                .thenReturn(List.of(Map.of(
                        "show",
                        show(2, 1396, "Breaking Bad", 62),
                        "seasons",
                        List.of(season(0, 5), season(1, 7)))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.progressCurrent()).isEqualTo(7);
        assertThat(entry.status()).isEqualTo(TrackingStatus.IN_PROGRESS);
    }

    @Test
    void aWatchlistedTitleIsPlanned() {
        when(client.watchlistMovies(ACCOUNT))
                .thenReturn(List.of(Map.of("listed_at", "2020-01-01T00:00:00.000Z", "movie", movie(3, 27205, "Inception"))));

        assertThat(adapter.pullLibrary(ACCOUNT).getFirst().status()).isEqualTo(TrackingStatus.PLANNING);
    }

    /** Having watched something outranks having meant to, whichever order they arrive in. */
    @Test
    void watchingSomethingOutranksTheWatchlist() {
        when(client.watchedMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", movie(1, 550, "Fight Club"))));
        when(client.watchlistMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", movie(1, 550, "Fight Club"))));

        List<ImportedEntry> entries = adapter.pullLibrary(ACCOUNT);

        assertThat(entries).hasSize(1);
        assertThat(entries.getFirst().status()).isEqualTo(TrackingStatus.COMPLETED);
    }

    @Test
    void aRatingEnrichesAnEntryOnTheShelf() {
        when(client.watchedMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", movie(1, 550, "Fight Club"))));
        when(client.ratedMovies(ACCOUNT)).thenReturn(List.of(Map.of("rating", 9, "movie", movie(1, 550, "Fight Club"))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.rawRating()).isEqualTo(9);
        assertThat(entry.rawRatingMax()).isEqualTo(10);
    }

    /**
     * A rating is not a claim to have watched anything — someone can rate off a
     * recommendation — so it never puts a title on the shelf by itself.
     */
    @Test
    void aRatingAloneDoesNotCreateAnEntry() {
        when(client.ratedMovies(ACCOUNT)).thenReturn(List.of(Map.of("rating", 9, "movie", movie(1, 550, "Fight Club"))));

        assertThat(adapter.pullLibrary(ACCOUNT)).isEmpty();
    }

    /** Trakt numbers films and shows separately, so the same number is two different titles. */
    @Test
    void aFilmAndAShowOfTheSameTraktNumberStayApart() {
        when(client.watchedMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", movie(550, 550, "A Film"))));
        when(client.watchedShows(ACCOUNT))
                .thenReturn(List.of(Map.of("show", show(550, 550, "A Show", 10), "seasons", List.of(season(1, 2)))));

        List<ImportedEntry> entries = adapter.pullLibrary(ACCOUNT);

        assertThat(entries).hasSize(2);
        assertThat(entries.stream().map(entry -> entry.itemRef().providerItemId()))
                .containsExactlyInAnyOrder("movie:550", "tv:550");
    }

    /** Nothing Trakt cannot identify goes any further; the resolver would have nothing to use. */
    @Test
    void anEntryWithoutATraktIdIsSkipped() {
        when(client.watchedMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", Map.of("title", "Nameless"))));

        assertThat(adapter.pullLibrary(ACCOUNT)).isEmpty();
    }

    /**
     * Trakt has no paused or dropped state, so an unfinished show must never be imported as
     * either — that would be a judgement the reader never made.
     */
    @Test
    void neverInventsAPausedOrDroppedStatus() {
        when(client.watchedShows(ACCOUNT))
                .thenReturn(List.of(Map.of("show", show(2, 1396, "Half Watched", 62), "seasons", List.of(season(1, 3)))));
        when(client.watchlistMovies(ACCOUNT)).thenReturn(List.of(Map.of("movie", movie(3, 27205, "Planned"))));

        assertThat(adapter.pullLibrary(ACCOUNT))
                .extracting(ImportedEntry::status)
                .doesNotContain(TrackingStatus.PAUSED, TrackingStatus.DROPPED);
    }
}
