package dev.nexus.core.exporting;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.UserEntry;
import dev.nexus.core.domain.UserEntryRepository;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes one of a reader's shelves out as a CSV they can keep.
 *
 * <p>One file per media type rather than one for the whole library: a spreadsheet of books
 * and films together has a column for chapters that half the rows cannot use, and the shelves
 * are read separately everywhere else in the app too.
 *
 * <p>The columns are this app's own, not any provider's. A file shaped like a Goodreads
 * export would be re-importable there and nowhere else, and would have to drop everything
 * Goodreads has no column for — which is most of what is worth carrying out.
 */
@Service
public class EntryExportService {

    /**
     * Source and external id travel together because they are what identifies a title
     * outside this app: "anilist 5114" survives a re-import where a title string does not.
     */
    private static final List<String> COLUMNS = List.of(
            "title",
            "media_type",
            "source",
            "external_id",
            "status",
            "rating_100",
            "progress_current",
            "progress_max",
            "progress_unit",
            "started_at",
            "finished_at",
            "favorite",
            "notes",
            "release_date",
            "imported_from",
            "updated_at");

    private final UserEntryRepository entries;

    public EntryExportService(UserEntryRepository entries) {
        this.entries = entries;
    }

    public record ExportedCsv(String filename, String content) {}

    /** Scoped to the caller's own rows by the query itself, as every read of entries is. */
    @Transactional(readOnly = true)
    public ExportedCsv export(Long userId, MediaType mediaType, LocalDate today) {
        CsvWriter writer = new CsvWriter(COLUMNS);
        for (UserEntry entry : entries.findByUserIdAndItemMediaTypeOrderByItemTitleAsc(userId, mediaType)) {
            writer.row(rowFor(entry));
        }
        return new ExportedCsv(filename(mediaType, today), writer.toCsv());
    }

    private List<String> rowFor(UserEntry entry) {
        TrackableItem item = entry.getItem();
        return Arrays.asList(
                item.getTitle(),
                text(item.getMediaType()),
                text(item.getSource()),
                item.getExternalId(),
                text(entry.getStatus()),
                text(entry.getRating()),
                text(entry.getProgressCurrent()),
                text(entry.getProgressMax()),
                text(entry.getProgressUnit()),
                text(entry.getStartedAt()),
                text(entry.getFinishedAt()),
                entry.isFavorite() ? "true" : "false",
                entry.getNotes(),
                text(item.getReleaseDate()),
                text(entry.getImportedFrom()),
                text(entry.getUpdatedAt()));
    }

    /** Dated, because an export is a snapshot and two of them are worth telling apart. */
    private String filename(MediaType mediaType, LocalDate today) {
        return "nexus-" + mediaType.name().toLowerCase(Locale.ROOT) + "-" + today + ".csv";
    }

    private String text(Object value) {
        return value == null ? null : value.toString();
    }
}
