package dev.nexus.core.exporting;

import dev.nexus.auth.CurrentUser;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.exporting.EntryExportService.ExportedCsv;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hands a reader their own shelf back as a file.
 *
 * <p>The counterpart to the CSV import route, and the answer to "is my list stuck in here" —
 * which is also what the DSGVO export obligation in plan.md §13 asks for.
 */
@RestController
@RequestMapping("/exports")
public class ExportController {

    /**
     * Games are deliberately not offered: a Steam library is not a list anyone keeps by hand,
     * and connecting the account brings the whole thing back whenever it is wanted.
     */
    private static final Set<MediaType> EXPORTABLE = EnumSet.complementOf(EnumSet.of(MediaType.GAME));

    /**
     * The byte order mark is for Excel alone: without it Excel reads a UTF-8 CSV in the local
     * codepage and every accented title arrives mangled. Every reader that matters skips it,
     * this app's own importer included.
     */
    private static final String BOM = "\ufeff";

    private final EntryExportService exports;

    public ExportController(EntryExportService exports) {
        this.exports = exports;
    }

    @GetMapping("/{mediaType}")
    public ResponseEntity<String> export(
            @AuthenticationPrincipal CurrentUser user, @PathVariable MediaType mediaType) {

        if (!EXPORTABLE.contains(mediaType)) {
            throw new ExportNotSupportedException("There is no CSV export for " + mediaType + ".");
        }

        ExportedCsv csv = exports.export(user.id(), mediaType, LocalDate.now());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + csv.filename() + "\"")
                // The filename is the browser's to read, and a cross-origin fetch cannot see
                // a header it is not offered.
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(BOM + csv.content());
    }
}
