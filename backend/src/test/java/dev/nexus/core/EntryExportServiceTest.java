package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import dev.nexus.core.exporting.EntryExportService;
import dev.nexus.core.exporting.EntryExportService.ExportedCsv;
import dev.nexus.core.importing.CsvTable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An export is only worth having if it carries what the app knows and nothing of anyone
 * else's. These pin both: the columns a row actually lands in, and that the query asked is
 * the one scoped to the caller.
 */
class EntryExportServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 27);

    private final UserEntryRepository entries = mock(UserEntryRepository.class);
    private final EntryExportService service = new EntryExportService(entries);

    @Test
    void writesTheEntryAndItsItemIntoOneRow() {
        UserEntry entry = entry("Berserk", MediaType.MANGA, "30002");
        entry.setRating((short) 95);
        entry.setProgressCurrent(364);
        entry.setProgressMax(null);
        entry.setProgressUnit(ProgressUnit.CHAPTERS);
        entry.setStartedAt(LocalDate.of(2024, 1, 2));
        entry.setFavorite(true);
        entry.setNotes("Hiatus, again.");
        entry.setImportedFrom(Provider.ANILIST);
        given(MediaType.MANGA, entry);

        CsvTable.Row row = firstRowOf(service.export(7L, MediaType.MANGA, TODAY));

        assertThat(row.value("title")).isEqualTo("Berserk");
        assertThat(row.value("media_type")).isEqualTo("MANGA");
        assertThat(row.value("source")).isEqualTo("ANILIST");
        assertThat(row.value("external_id")).isEqualTo("30002");
        assertThat(row.value("status")).isEqualTo("IN_PROGRESS");
        assertThat(row.value("rating_100")).isEqualTo("95");
        assertThat(row.value("progress_current")).isEqualTo("364");
        assertThat(row.value("progress_unit")).isEqualTo("CHAPTERS");
        assertThat(row.value("started_at")).isEqualTo("2024-01-02");
        assertThat(row.value("favorite")).isEqualTo("true");
        assertThat(row.value("notes")).isEqualTo("Hiatus, again.");
        assertThat(row.value("imported_from")).isEqualTo("ANILIST");
    }

    /** Everything optional is genuinely optional; an untouched entry still exports cleanly. */
    @Test
    void leavesUnsetFieldsEmptyRatherThanWritingNull() {
        given(MediaType.BOOK, entry("Dune", MediaType.BOOK, "OL893415W"));

        String csv = service.export(7L, MediaType.BOOK, TODAY).content();

        assertThat(csv).doesNotContain("null");
        assertThat(firstRowOf(service.export(7L, MediaType.BOOK, TODAY)).value("rating_100"))
                .isNull();
    }

    /** The header is written whether or not there is anything under it. */
    @Test
    void writesAHeaderOnlyFileForAnEmptyShelf() {
        given(MediaType.ANIME, new UserEntry[0]);

        ExportedCsv export = service.export(7L, MediaType.ANIME, TODAY);

        assertThat(export.content()).startsWith("\"title\",\"media_type\"");
        assertThat(CsvTable.parse(export.content()).rows()).isEmpty();
    }

    @Test
    void namesTheFileAfterTheShelfAndTheDay() {
        given(MediaType.ANIME, new UserEntry[0]);

        assertThat(service.export(7L, MediaType.ANIME, TODAY).filename()).isEqualTo("nexus-anime-2026-08-27.csv");
    }

    /** The only query used is the one scoped to this user, which is what keeps shelves private. */
    @Test
    void readsOnlyTheCallersOwnRowsOfThatType() {
        given(MediaType.ANIME, new UserEntry[0]);

        service.export(7L, MediaType.ANIME, TODAY);

        verify(entries).findByUserIdAndItemMediaTypeOrderByItemTitleAsc(7L, MediaType.ANIME);
    }

    private void given(MediaType mediaType, UserEntry... rows) {
        when(entries.findByUserIdAndItemMediaTypeOrderByItemTitleAsc(7L, mediaType))
                .thenReturn(List.of(rows));
    }

    private CsvTable.Row firstRowOf(ExportedCsv export) {
        return CsvTable.parse(export.content()).rows().getFirst();
    }

    private UserEntry entry(String title, MediaType mediaType, String externalId) {
        TrackableItem item = new TrackableItem(
                mediaType,
                Source.ANILIST,
                externalId,
                title,
                null,
                LocalDate.of(1989, 8, 25),
                ItemState.RELEASED,
                Map.of());
        return new UserEntry(7L, item, TrackingStatus.IN_PROGRESS);
    }
}
