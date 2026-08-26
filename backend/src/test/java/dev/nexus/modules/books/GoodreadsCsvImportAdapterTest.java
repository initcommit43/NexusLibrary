package dev.nexus.modules.books;

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

/**
 * The only route in for books, so this file stands in for both a CSV importer's tests and a
 * live adapter's. The header line in each case is the real one Goodreads exports.
 */
class GoodreadsCsvImportAdapterTest {

    /** Trimmed to the columns that matter; a real export carries two dozen more. */
    private static final String HEADER =
            "Book Id,Title,Author,ISBN,ISBN13,My Rating,Number of Pages,Date Read,Exclusive Shelf\n";

    private final GoodreadsCsvImportAdapter adapter = new GoodreadsCsvImportAdapter();

    private List<ImportedEntry> parse(String csv) {
        return adapter.parse(CsvTable.parse(csv));
    }

    @Test
    void carriesTheIdsTheResolverReads() {
        ImportedEntry entry = parse(HEADER
                        + "104,Dune,Frank Herbert,\"=\"\"0441013597\"\"\",\"=\"\"9780441013593\"\"\",5,604,2021/03/14,read\n")
                .getFirst();

        assertThat(entry.itemRef().provider()).isEqualTo(Provider.GOODREADS);
        assertThat(entry.itemRef().providerItemId()).isEqualTo("104");
        assertThat(entry.itemRef().hints())
                .containsEntry(GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT, "104")
                .containsEntry(GoodreadsToOpenLibraryResolver.ISBN13_HINT, "9780441013593")
                .containsEntry(GoodreadsToOpenLibraryResolver.ISBN10_HINT, "0441013597")
                .containsEntry(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, "Frank Herbert");
    }

    /**
     * Goodreads wraps both ISBN columns in an Excel formula so a spreadsheet keeps the leading
     * zeros. Taken literally that string is not an ISBN and matches nothing.
     */
    @Test
    void unwrapsTheExcelEscapingAroundIsbns() {
        ImportedEntry entry = parse(
                        HEADER + "1,A Book,An Author,\"=\"\"0441013597\"\"\",\"=\"\"9780441013593\"\"\",0,100,,to-read\n")
                .getFirst();

        assertThat(entry.itemRef().hints())
                .containsEntry(GoodreadsToOpenLibraryResolver.ISBN13_HINT, "9780441013593")
                .containsEntry(GoodreadsToOpenLibraryResolver.ISBN10_HINT, "0441013597");
    }

    /** An absent ISBN arrives as an empty formula, which must not become an empty-string hint. */
    @Test
    void treatsAnEmptyIsbnFormulaAsNoIsbnAtAll() {
        ImportedEntry entry =
                parse(HEADER + "1,A Book,An Author,\"=\"\"\"\"\",\"=\"\"\"\"\",0,100,,to-read\n").getFirst();

        assertThat(entry.itemRef().hints())
                .doesNotContainKey(GoodreadsToOpenLibraryResolver.ISBN13_HINT)
                .doesNotContainKey(GoodreadsToOpenLibraryResolver.ISBN10_HINT);
    }

    /** An ISBN-10 can end in a check digit of X, which is not a digit and must survive. */
    @Test
    void keepsTheCheckLetterOnAnIsbn10() {
        ImportedEntry entry =
                parse(HEADER + "1,A Book,An Author,\"=\"\"156389016X\"\"\",\"=\"\"\"\"\",0,100,,read\n").getFirst();

        assertThat(entry.itemRef().hints()).containsEntry(GoodreadsToOpenLibraryResolver.ISBN10_HINT, "156389016X");
    }

    @Test
    void translatesGoodreadsShelfNames() {
        List<ImportedEntry> entries = parse(HEADER
                + "1,A,X,,,0,100,,read\n"
                + "2,B,X,,,0,100,,currently-reading\n"
                + "3,C,X,,,0,100,,to-read\n");

        assertThat(entries)
                .extracting(ImportedEntry::status)
                .containsExactly(TrackingStatus.COMPLETED, TrackingStatus.IN_PROGRESS, TrackingStatus.PLANNING);
    }

    /** Goodreads writes dates with slashes where every other export here uses dashes. */
    @Test
    void readsTheSlashSeparatedDateFormat() {
        ImportedEntry entry = parse(HEADER + "1,A Book,An Author,,,4,300,2021/03/14,read\n")
                .getFirst();

        assertThat(entry.finishedAt()).isEqualTo(LocalDate.of(2021, 3, 14));
    }

    @Test
    void keepsRatingsOnGoodreadsOwnScaleAndDropsTheUnrated() {
        List<ImportedEntry> entries =
                parse(HEADER + "1,A,X,,,4,300,,read\n" + "2,B,X,,,0,300,,read\n");

        assertThat(entries.get(0).rawRating()).isEqualTo(4);
        assertThat(entries.get(0).rawRatingMax()).isEqualTo(5);
        assertThat(entries.get(1).rawRating()).isNull();
    }

    /**
     * Goodreads records no page-level progress at all, so the only honest current value is the
     * one a finished book implies. An unfinished one shows a total and no position.
     */
    @Test
    void countsPagesAsProgressOnlyForAFinishedBook() {
        List<ImportedEntry> entries =
                parse(HEADER + "1,A,X,,,0,300,,read\n" + "2,B,X,,,0,300,,currently-reading\n");

        assertThat(entries.get(0).progressCurrent()).isEqualTo(300);
        assertThat(entries.get(0).progressMax()).isEqualTo(300);
        assertThat(entries.get(0).progressUnit()).isEqualTo(ProgressUnit.PAGES);
        assertThat(entries.get(1).progressCurrent()).isNull();
        assertThat(entries.get(1).progressMax()).isEqualTo(300);
    }

    /**
     * A row with no id of any kind still becomes an entry. It resolves by title or not at all,
     * and either way the reader sees it — a file that quietly imports most of itself tells
     * them nothing about the rest.
     */
    @Test
    void keepsARowThatHasOnlyATitle() {
        ImportedEntry entry = parse(HEADER + ",Some Obscure Book,,,,0,,,to-read\n").getFirst();

        assertThat(entry.itemRef().providerItemId()).isEqualTo("Some Obscure Book");
        assertThat(entry.itemRef().hints()).doesNotContainKey(GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT);
    }

    @Test
    void refusesAFileWithNoTitleColumn() {
        assertThatExceptionOfType(CsvFormatException.class)
                .isThrownBy(() -> parse("Name,Rating\nSomething,4\n"))
                .withMessageContaining("Title");
    }
}
