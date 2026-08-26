package dev.nexus.modules.books;

import dev.nexus.core.adapter.ExternalItemRef;
import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.ProgressUnit;
import dev.nexus.core.domain.Provider;
import dev.nexus.core.domain.TrackingStatus;
import dev.nexus.core.importing.CsvFormatException;
import dev.nexus.core.importing.CsvImportAdapter;
import dev.nexus.core.importing.CsvStatuses;
import dev.nexus.core.importing.CsvTable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Reads a Goodreads export. This is the only route in for books: Goodreads closed its API to
 * new keys in 2020 and has not reopened it, so there is no account to connect and the CSV is
 * not a fallback but the entire integration.
 *
 * <p>What resolves a row is first Goodreads' own {@code Book Id}: Open Library indexes it, so
 * {@link GoodreadsToOpenLibraryResolver} can match by identity rather than by string. The two
 * ISBN columns follow it as equally exact but narrower fallbacks — Goodreads leaves the ISBN
 * blank for ebooks and for editions catalogued by hand.
 *
 * <p>A row with none of the three falls back to a title search, which the Simkl importer
 * deliberately does not do. A book has no equivalent of the film-versus-show collision that
 * makes guessing dangerous there, and the alternative is silently dropping a share of a
 * library that a reader would have to find for themselves.
 */
@Component
public class GoodreadsCsvImportAdapter implements CsvImportAdapter {

    /** Goodreads rates on 1-5, and writes an unrated book as 0. */
    private static final int RATING_MAX = 5;

    private static final String[] TITLE_COLUMNS = {"title"};
    private static final String[] AUTHOR_COLUMNS = {"author", "author l-f", "primary author"};
    private static final String[] ISBN13_COLUMNS = {"isbn13", "isbn 13"};
    private static final String[] ISBN10_COLUMNS = {"isbn", "isbn10", "isbn 10"};
    private static final String[] BOOK_ID_COLUMNS = {"book id", "bookid", "goodreads book id"};
    private static final String[] SHELF_COLUMNS = {"exclusive shelf", "exclusiveshelf", "shelf", "bookshelves"};
    private static final String[] PAGES_COLUMNS = {"number of pages", "numberofpages", "pages"};

    @Override
    public Provider provider() {
        return Provider.GOODREADS;
    }

    @Override
    public List<ImportedEntry> parse(CsvTable table) {
        if (!table.has(TITLE_COLUMNS)) {
            throw new CsvFormatException("That file has no Title column, which every Goodreads "
                    + "export has. Export yours from goodreads.com/review/import.");
        }

        List<ImportedEntry> entries = new ArrayList<>();
        for (CsvTable.Row row : table.rows()) {
            String title = row.value(TITLE_COLUMNS);
            if (title == null) {
                continue;
            }

            TrackingStatus status = CsvStatuses.of(row.value(SHELF_COLUMNS), TrackingStatus.PLANNING);
            Integer rating = rating(row);
            Integer pages = row.number(PAGES_COLUMNS);

            entries.add(new ImportedEntry(
                    itemRef(row, title),
                    status,
                    // Goodreads records no page-level progress, so the only honest current
                    // value is the one a finished book implies.
                    status == TrackingStatus.COMPLETED ? pages : null,
                    pages,
                    pages == null ? null : ProgressUnit.PAGES,
                    rating,
                    rating == null ? null : RATING_MAX,
                    null,
                    row.date("date read", "dateread", "read at")));
        }
        return entries;
    }

    /**
     * The row's identity is Goodreads' own book id where there is one, so a second import of a
     * refreshed export updates each entry instead of doubling it. Falling back to the ISBN and
     * then the title keeps a hand-edited file usable, at the cost of that stability.
     */
    private ExternalItemRef itemRef(CsvTable.Row row, String title) {
        String bookId = row.value(BOOK_ID_COLUMNS);
        String isbn13 = isbn(row, ISBN13_COLUMNS);
        String isbn10 = isbn(row, ISBN10_COLUMNS);
        String author = row.value(AUTHOR_COLUMNS);

        Map<String, String> hints = new LinkedHashMap<>();
        if (bookId != null) {
            hints.put(GoodreadsToOpenLibraryResolver.GOODREADS_ID_HINT, bookId);
        }
        if (isbn13 != null) {
            hints.put(GoodreadsToOpenLibraryResolver.ISBN13_HINT, isbn13);
        }
        if (isbn10 != null) {
            hints.put(GoodreadsToOpenLibraryResolver.ISBN10_HINT, isbn10);
        }
        if (author != null) {
            hints.put(GoodreadsToOpenLibraryResolver.AUTHOR_HINT, author);
        }

        String identity = firstOf(bookId, isbn13, isbn10, title);
        return new ExternalItemRef(Provider.GOODREADS, identity, title, Map.copyOf(hints));
    }

    /**
     * Goodreads writes both ISBN columns Excel-escaped — {@code ="0441013597"} — so that a
     * spreadsheet keeps the leading zeros instead of reading the value as a number. Taken
     * literally that is not an ISBN and matches nothing; an absent one arrives as {@code =""}
     * and has to come back null rather than as an empty string.
     */
    private String isbn(CsvTable.Row row, String... columnNames) {
        String raw = row.value(columnNames);
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9Xx]", "");
        return digits.isBlank() ? null : digits.toUpperCase();
    }

    /** Goodreads writes an unrated book as 0, which is not a rating anyone gave. */
    private Integer rating(CsvTable.Row row) {
        Integer rating = row.number("my rating", "myrating", "rating");
        return rating == null || rating == 0 ? null : rating;
    }

    private String firstOf(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
