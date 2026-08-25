package dev.nexus.modules.anime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvTable;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The strictest of the four adapters, because AniList ids are treated as canonical outright:
 * a wrong id here does not fail to match, it matches the wrong title silently.
 */
class AniListCsvImportAdapterTest {

    private final AniListCsvImportAdapter adapter = new AniListCsvImportAdapter();

    private List<ImportedEntry> parse(String csv) {
        return adapter.parse(CsvTable.parse(csv));
    }

    @Test
    void readsAnAniListExport() {
        ImportedEntry entry = parse("anilist_id,title,status,score,progress\n"
                        + "5114,Fullmetal Alchemist: Brotherhood,COMPLETED,95,64\n")
                .getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.ANILIST);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("5114");
        assertThat(entry.status()).isEqualTo(TrackingStatus.COMPLETED);
        assertThat(entry.progressCurrent()).isEqualTo(64);
    }

    /**
     * An id column that does not name AniList could be a MyAnimeList id, and treating that as
     * canonical would put the wrong show on the shelf with no error anywhere.
     */
    @Test
    void refusesAFileWhoseIdColumnDoesNotNameAniList() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> parse("id,title,status\n5114,Fullmetal Alchemist,COMPLETED\n"))
                .withMessageContaining("AniList id");
    }

    /** AniList lets a reader pick a display scale, so exports arrive on 0-10 or 0-100. */
    @Test
    void readsAHundredPointScaleAsSuch() {
        List<ImportedEntry> entries = parse("anilist_id,title,score\n1,A,95\n2,B,70\n");

        assertThat(entries.getFirst().rawRating()).isEqualTo(95);
        assertThat(entries.getFirst().rawRatingMax()).isEqualTo(100);
    }

    /**
     * The whole file decides the scale. Judging row by row would read a 7 out of 100 as a 7
     * out of 10 and turn a poor score into a good one.
     */
    @Test
    void aFileWithOnlySmallScoresIsATenPointScale() {
        List<ImportedEntry> entries = parse("anilist_id,title,score\n1,A,9\n2,B,7\n");

        assertThat(entries.getFirst().rawRatingMax()).isEqualTo(10);
    }

    @Test
    void aZeroScoreIsNoScore() {
        assertThat(parse("anilist_id,title,score\n1,A,0\n").getFirst().rawRating()).isNull();
    }

    @Test
    void skipsRowsWithoutAnId() {
        assertThat(parse("anilist_id,title\n,A\n5114,B\n")).hasSize(1);
    }
}
