package dev.nexus.modules.film;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvTable;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The upload route for films and shows, which has to resolve exactly as the API one does. */
class SimklCsvImportAdapterTest {

    private final SimklCsvImportAdapter adapter = new SimklCsvImportAdapter();

    private List<ImportedEntry> parse(String csv) {
        return adapter.parse(CsvTable.parse(csv));
    }

    /** Same hints the live adapter sets, so the same resolver places the row unchanged. */
    @Test
    void carriesTheIdsTheResolverReads() {
        ImportedEntry entry = parse("Simkl ID,Title,Type,TMDB,IMDB,Watchlist\n"
                        + "12,Fight Club,movie,550,tt0137523,completed\n")
                .getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.SIMKL);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("movie:12");
        assertThat(entry.itemRef().hints())
                .containsEntry(SimklToTmdbResolver.TMDB_ID_HINT, "550")
                .containsEntry(SimklToTmdbResolver.IMDB_ID_HINT, "tt0137523")
                .containsEntry(SimklToTmdbResolver.KIND_HINT, "movie");
    }

    @Test
    void translatesSimklsStatusWords() {
        List<ImportedEntry> entries = parse("Simkl ID,Title,TMDB,Watchlist\n"
                + "1,A,1,completed\n"
                + "2,B,2,watching\n"
                + "3,C,3,plantowatch\n"
                + "4,D,4,hold\n"
                + "5,E,5,dropped\n");

        assertThat(entries)
                .extracting(ImportedEntry::status)
                .containsExactly(
                        TrackingStatus.COMPLETED,
                        TrackingStatus.IN_PROGRESS,
                        TrackingStatus.PLANNING,
                        TrackingStatus.PAUSED,
                        TrackingStatus.DROPPED);
    }

    /** "Plan to watch" with spaces is the same shelf as "plantowatch" without them. */
    @Test
    void readsStatusWordsHoweverTheyArePunctuated() {
        assertThat(parse("Simkl ID,Title,TMDB,Watchlist\n1,A,1,Plan to Watch\n")
                        .getFirst()
                        .status())
                .isEqualTo(TrackingStatus.PLANNING);
    }

    @Test
    void tellsFilmsAndShowsApartByTheirTypeColumn() {
        List<ImportedEntry> entries =
                parse("Simkl ID,Title,Type,TMDB\n1,A Film,movie,550\n2,A Show,tv,550\n");

        assertThat(entries)
                .extracting(entry -> entry.itemRef().providerItemId())
                .containsExactly("movie:1", "tv:2");

        // Same TMDB number on both rows, kept apart by the kind alone.
        assertThat(entries.get(0).itemRef().hints()).containsEntry(SimklToTmdbResolver.KIND_HINT, "movie");
        assertThat(entries.get(1).itemRef().hints()).containsEntry(SimklToTmdbResolver.KIND_HINT, "tv");
    }

    /**
     * Without a type column, only a show has a last episode watched — and getting this wrong
     * would file a show under a film's TMDB number, the collision TmdbKind exists to prevent.
     */
    @Test
    void fallsBackToTheEpisodeColumnToTellThemApart() {
        List<ImportedEntry> entries = parse("Simkl ID,Title,TMDB,Last Episode Watched\n"
                + "1,A Film,550,\n"
                + "2,A Show,1396,7\n");

        assertThat(entries.get(0).itemRef().hints()).containsEntry(SimklToTmdbResolver.KIND_HINT, "movie");
        assertThat(entries.get(1).itemRef().hints()).containsEntry(SimklToTmdbResolver.KIND_HINT, "tv");
        assertThat(entries.get(1).progressCurrent()).isEqualTo(7);
        assertThat(entries.get(1).progressUnit()).isEqualTo(ProgressUnit.EPISODES);
    }

    @Test
    void carriesRatingsAndWatchDates() {
        ImportedEntry entry = parse("Simkl ID,Title,TMDB,Watchlist,Rating,Last Watch Date\n"
                        + "1,Fight Club,550,completed,9,2019-04-26T18:30:00Z\n")
                .getFirst();

        assertThat(entry.rawRating()).isEqualTo(9);
        assertThat(entry.rawRatingMax()).isEqualTo(10);
        assertThat(entry.finishedAt()).isEqualTo(LocalDate.of(2019, 4, 26));
    }

    /** An unrated title has no rating, not a zero somebody would read as an opinion. */
    @Test
    void anUnratedTitleCarriesNoRating() {
        ImportedEntry entry = parse("Simkl ID,Title,TMDB,Rating\n1,A,550,0\n").getFirst();

        assertThat(entry.rawRating()).isNull();
        assertThat(entry.rawRatingMax()).isNull();
    }

    /**
     * A row with no id cannot be resolved — but it is still reported, because a file of
     * thirty titles that quietly imports twenty-nine tells the reader nothing about the one.
     */
    @Test
    void keepsRowsWithNeitherIdSoTheyReachTheUnmatchedReport() {
        List<ImportedEntry> entries = parse("Simkl ID,Title,TMDB,IMDB\n1,A,550,\n2,Nothing To Match,,\n");

        assertThat(entries).hasSize(2);
        assertThat(entries.get(1).itemRef().title()).isEqualTo("Nothing To Match");
        assertThat(entries.get(1).itemRef().hints())
                .doesNotContainKey(SimklToTmdbResolver.TMDB_ID_HINT)
                .doesNotContainKey(SimklToTmdbResolver.IMDB_ID_HINT);
    }

    /** Nothing to call itself by at all is the one row still dropped. */
    @Test
    void dropsARowWithNoIdAndNoTitle() {
        assertThat(parse("Simkl ID,Title,TMDB,IMDB\n1,A,550,\n,,,\n")).hasSize(1);
    }

    /** The wrong file should say so at once, not import zero titles and call it a success. */
    @Test
    void refusesAFileWithNoIdColumnAtAll() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> parse("Title,Year,Watchlist\nFight Club,1999,completed\n"))
                .withMessageContaining("TMDB or IMDb");
    }

    /** Without Simkl's own id the row still has to be filed under something stable. */
    @Test
    void fallsBackToTheTmdbIdWhenSimklsOwnIsAbsent() {
        ImportedEntry entry = parse("Title,Type,TMDB\nFight Club,movie,550\n").getFirst();

        assertThat(entry.itemRef().providerItemId()).isEqualTo("movie:550");
        assertThat(entry.itemRef().title()).isEqualTo("Fight Club");
    }
}
