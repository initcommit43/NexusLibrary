package dev.nexus.modules.games;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvTable;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Steam publishes no export of its own, so this reads what the community exporters write. */
class SteamCsvImportAdapterTest {

    private final SteamCsvImportAdapter adapter = new SteamCsvImportAdapter();

    private List<ImportedEntry> parse(String csv) {
        return adapter.parse(CsvTable.parse(csv));
    }

    @Test
    void readsAppIdsAndPlaytime() {
        ImportedEntry entry = parse("appid,name,playtime_forever\n70,Half-Life,1200\n").getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.STEAM);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("70");
        assertThat(entry.itemRef().title()).isEqualTo("Half-Life");
        assertThat(entry.progressCurrent()).isEqualTo(1200);
        assertThat(entry.progressUnit()).isEqualTo(ProgressUnit.MINUTES);
    }

    /** An exporter writing hours means hours; the games module counts in minutes. */
    @Test
    void convertsAnHoursColumnToMinutes() {
        ImportedEntry entry = parse("appid,name,hours played\n70,Half-Life,20\n").getFirst();

        assertThat(entry.progressCurrent()).isEqualTo(1200);
    }

    /** The same reading the API import makes: played is in progress, untouched is backlog. */
    @Test
    void infersStatusFromPlaytimeWhenTheFileDoesNotSayOne() {
        List<ImportedEntry> entries = parse("appid,name,playtime_forever\n70,Played,1200\n440,Untouched,0\n");

        assertThat(entries.get(0).status()).isEqualTo(TrackingStatus.IN_PROGRESS);
        assertThat(entries.get(1).status()).isEqualTo(TrackingStatus.PLANNING);
    }

    /** A shelf the reader arranged by hand outranks what playtime implies. */
    @Test
    void prefersAStatusColumnWhenTheExportHasOne() {
        ImportedEntry entry =
                parse("appid,name,playtime_forever,status\n70,Half-Life,1200,completed\n").getFirst();

        assertThat(entry.status()).isEqualTo(TrackingStatus.COMPLETED);
    }

    @Test
    void refusesAFileWithNoAppIdColumn() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> parse("name,hours\nHalf-Life,20\n"))
                .withMessageContaining("appid");
    }
}
