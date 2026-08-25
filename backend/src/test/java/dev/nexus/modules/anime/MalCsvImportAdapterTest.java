package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvTable;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The upload route for MyAnimeList, whose own export is XML — so this reads what the common
 * converters write, keeping MAL's column names.
 */
class MalCsvImportAdapterTest {

    private final MalCsvImportAdapter adapter = new MalCsvImportAdapter();

    private List<ImportedEntry> parse(String csv) {
        return adapter.parse(CsvTable.parse(csv));
    }

    @Test
    void readsMalsOwnColumnNames() {
        ImportedEntry entry = parse(
                        "series_animedb_id,series_title,my_status,my_score,my_watched_episodes,series_episodes\n"
                                + "5114,Fullmetal Alchemist: Brotherhood,Completed,10,64,64\n")
                .getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.MAL);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("5114");
        assertThat(entry.itemRef().title()).isEqualTo("Fullmetal Alchemist: Brotherhood");
        assertThat(entry.status()).isEqualTo(TrackingStatus.COMPLETED);
        assertThat(entry.rawRating()).isEqualTo(10);
        assertThat(entry.rawRatingMax()).isEqualTo(10);
        assertThat(entry.progressCurrent()).isEqualTo(64);
        assertThat(entry.progressMax()).isEqualTo(64);
        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.EPISODES);
    }

    /** MAL numbers anime and manga separately, so the join is only meaningful within a type. */
    @Test
    void tellsAnimeAndMangaApartByTheirTypeColumn() {
        List<ImportedEntry> entries =
                parse("mal_id,title,type,my_status\n1,An Anime,anime,watching\n2,A Manga,manga,reading\n");

        assertThat(entries.get(0).itemRef().hints())
                .containsEntry(MalLibraryImportAdapter.HINT_MEDIA_TYPE, MediaType.ANIME.name());
        assertThat(entries.get(1).itemRef().hints())
                .containsEntry(MalLibraryImportAdapter.HINT_MEDIA_TYPE, MediaType.MANGA.name());
        assertThat(entries.get(1).progressUnit()).isEqualTo(ProgressUnit.CHAPTERS);
    }

    /** A file that only ever mentions chapters is a manga list whatever it calls itself. */
    @Test
    void fallsBackToWhatTheRowCountsWhenNoTypeIsGiven() {
        ImportedEntry entry = parse("series_mangadb_id,series_title,my_read_chapters,series_chapters\n"
                        + "11,Berserk,364,0\n")
                .getFirst();

        assertThat(entry.itemRef().hints())
                .containsEntry(MalLibraryImportAdapter.HINT_MEDIA_TYPE, MediaType.MANGA.name());
        assertThat(entry.progressCurrent()).isEqualTo(364);
    }

    /** Everything the resolver's fallback weighs travels along, as it does on the API import. */
    @Test
    void carriesTheHintsATitleMatchWouldNeed() {
        ImportedEntry entry = parse("mal_id,series_title,series_title_english,series_episodes,my_status\n"
                        + "1,Cowboy Bebop,Cowboy Bebop,26,completed\n")
                .getFirst();

        assertThat(entry.itemRef().hints())
                .containsEntry(MalLibraryImportAdapter.HINT_TITLE_EN, "Cowboy Bebop")
                .containsEntry(MalLibraryImportAdapter.HINT_EPISODES, "26");
    }

    @Test
    void readsMalsStatusWordsIncludingItsHyphenatedOne() {
        List<ImportedEntry> entries = parse("mal_id,title,my_status\n"
                + "1,A,Watching\n"
                + "2,B,Completed\n"
                + "3,C,On-Hold\n"
                + "4,D,Dropped\n"
                + "5,E,Plan to Watch\n");

        assertThat(entries)
                .extracting(ImportedEntry::status)
                .containsExactly(
                        TrackingStatus.IN_PROGRESS,
                        TrackingStatus.COMPLETED,
                        TrackingStatus.PAUSED,
                        TrackingStatus.DROPPED,
                        TrackingStatus.PLANNING);
    }

    /** MAL writes an unscored entry as 0, which is not an opinion anyone expressed. */
    @Test
    void aZeroScoreIsNoScore() {
        ImportedEntry entry = parse("mal_id,title,my_score\n1,A,0\n").getFirst();

        assertThat(entry.rawRating()).isNull();
        assertThat(entry.rawRatingMax()).isNull();
    }

    @Test
    void readsTheDatesMalWrites() {
        ImportedEntry entry =
                parse("mal_id,title,my_start_date,my_finish_date\n1,A,2019-04-26,0000-00-00\n").getFirst();

        assertThat(entry.startedAt()).isEqualTo(LocalDate.of(2019, 4, 26));
        assertThat(entry.finishedAt()).isNull();
    }

    @Test
    void refusesAFileWithNoMalIdColumn() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> parse("title,my_status\nCowboy Bebop,completed\n"))
                .withMessageContaining("MyAnimeList id");
    }
}
