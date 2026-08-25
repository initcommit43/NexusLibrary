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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pins the translation of Simkl's shelf into this one.
 *
 * <p>Simkl keeps a real status per title, so unlike the anime imports there is almost
 * nothing to infer — which makes the mapping itself the thing worth pinning, since a status
 * quietly translated wrong produces a plausible library that misstates what someone watched.
 */
class SimklLibraryImportAdapterTest {

    private static final ExternalAccount ACCOUNT = new ExternalAccount(1L, Provider.SIMKL, "reader");

    private SimklClient client;
    private SimklLibraryImportAdapter adapter;

    @BeforeEach
    void setUp() {
        client = mock(SimklClient.class);
        when(client.movies(ACCOUNT)).thenReturn(List.of());
        when(client.shows(ACCOUNT)).thenReturn(List.of());
        adapter = new SimklLibraryImportAdapter(client);
    }

    private Map<String, Object> movieRow(String status, Map<String, Object> extra) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status);
        row.put(
                "movie",
                Map.of("title", "Fight Club", "year", 1999, "ids", Map.of("simkl", 12, "imdb", "tt0137523", "tmdb", 550)));
        row.putAll(extra);
        return row;
    }

    private Map<String, Object> showRow(String status, int watched, int total, Map<String, Object> extra) {
        Map<String, Object> row = new HashMap<>();
        row.put("status", status);
        row.put("watched_episodes_count", watched);
        row.put("total_episodes_count", total);
        row.put(
                "show",
                Map.of("title", "Breaking Bad", "ids", Map.of("simkl", 34, "imdb", "tt0903747", "tmdb", 1396)));
        row.putAll(extra);
        return row;
    }

    /** Simkl's five words and this app's five words are the same five words. */
    @Test
    void translatesEveryStatusSimklCanReport() {
        when(client.shows(ACCOUNT))
                .thenReturn(List.of(
                        showRow("watching", 1, 10, Map.of()),
                        showRow("plantowatch", 0, 10, Map.of()),
                        showRow("hold", 3, 10, Map.of()),
                        showRow("completed", 10, 10, Map.of()),
                        showRow("dropped", 2, 10, Map.of())));

        assertThat(adapter.pullLibrary(ACCOUNT))
                .extracting(ImportedEntry::status)
                .containsExactly(
                        TrackingStatus.IN_PROGRESS,
                        TrackingStatus.PLANNING,
                        TrackingStatus.PAUSED,
                        TrackingStatus.COMPLETED,
                        TrackingStatus.DROPPED);
    }

    /** A word nobody here knows belongs on the shelf, not in the bin. */
    @Test
    void anUnknownStatusIsPlannedRatherThanDropped() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("something-new", Map.of())));

        assertThat(adapter.pullLibrary(ACCOUNT).getFirst().status()).isEqualTo(TrackingStatus.PLANNING);
    }

    @Test
    void aShowCarriesItsEpisodeProgress() {
        when(client.shows(ACCOUNT)).thenReturn(List.of(showRow("watching", 7, 62, Map.of())));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.progressCurrent()).isEqualTo(7);
        assertThat(entry.progressMax()).isEqualTo(62);
        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.EPISODES);
    }

    /** A film has no episodes, so it carries no progress rather than a made-up 0 of 1. */
    @Test
    void aFilmCarriesNoProgress() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("completed", Map.of())));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.progressCurrent()).isNull();
        assertThat(entry.progressMax()).isNull();
        assertThat(entry.progressUnit()).isNull();
    }

    @Test
    void aFinishDateIsKeptOnlyForWhatIsFinished() {
        when(client.movies(ACCOUNT))
                .thenReturn(List.of(
                        movieRow("completed", Map.of("last_watched_at", "2019-04-26T18:30:00Z")),
                        movieRow("dropped", Map.of("last_watched_at", "2019-04-26T18:30:00Z"))));

        List<ImportedEntry> entries = adapter.pullLibrary(ACCOUNT);

        assertThat(entries.get(0).finishedAt()).isEqualTo(LocalDate.of(2019, 4, 26));
        assertThat(entries.get(1).finishedAt()).isNull();
    }

    @Test
    void carriesTheRatingOnSimklsOwnScale() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("completed", Map.of("user_rating", 9))));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.rawRating()).isEqualTo(9);
        assertThat(entry.rawRatingMax()).isEqualTo(10);
    }

    /** An unrated title has no rating at all — not a zero somebody would read as an opinion. */
    @Test
    void anUnratedTitleCarriesNoRating() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("completed", Map.of())));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.rawRating()).isNull();
        assertThat(entry.rawRatingMax()).isNull();
    }

    /** Both ids travel: the TMDB one resolves for free, the IMDb one is the fallback. */
    @Test
    void carriesTheTmdbAndImdbIdsAsHints() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("completed", Map.of())));

        ImportedEntry entry = adapter.pullLibrary(ACCOUNT).getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.SIMKL);
        assertThat(entry.itemRef().hints())
                .containsEntry(SimklToTmdbResolver.TMDB_ID_HINT, "550")
                .containsEntry(SimklToTmdbResolver.IMDB_ID_HINT, "tt0137523")
                .containsEntry(SimklToTmdbResolver.KIND_HINT, "movie");
    }

    /** Simkl numbers films and shows separately too, so the kind travels with the id. */
    @Test
    void providerIdsCarryTheirKind() {
        when(client.movies(ACCOUNT)).thenReturn(List.of(movieRow("completed", Map.of())));
        when(client.shows(ACCOUNT)).thenReturn(List.of(showRow("watching", 1, 10, Map.of())));

        assertThat(adapter.pullLibrary(ACCOUNT))
                .extracting(entry -> entry.itemRef().providerItemId())
                .containsExactly("movie:12", "tv:34");
    }

    /** Nothing Simkl cannot identify goes further; the resolver would have nothing to use. */
    @Test
    void anEntryWithoutASimklIdIsSkipped() {
        Map<String, Object> row = new HashMap<>();
        row.put("status", "completed");
        row.put("movie", Map.of("title", "Nameless"));
        when(client.movies(ACCOUNT)).thenReturn(List.of(row));

        assertThat(adapter.pullLibrary(ACCOUNT)).isEmpty();
    }

    /** Anime is the anime module's shelf, on AniList canonicals; importing it here would duplicate it. */
    @Test
    void neverAsksSimklForAnime() {
        adapter.pullLibrary(ACCOUNT);

        org.mockito.Mockito.verify(client).movies(ACCOUNT);
        org.mockito.Mockito.verify(client).shows(ACCOUNT);
        org.mockito.Mockito.verifyNoMoreInteractions(client);
    }
}
