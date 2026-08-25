package dev.nexus.core.importing;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.Provider;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns an uploaded export into the rows the import pipeline already knows how to place.
 *
 * <p>Parsing happens on the request thread rather than inside the background job, and
 * deliberately: a file with the wrong columns should be refused while the reader is still
 * looking at the upload button, not reported minutes later as a failed import.
 */
@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final List<CsvImportAdapter> adapters;

    public CsvImportService(List<CsvImportAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    /** Which providers offer this route at all, so the settings page can show it honestly. */
    public boolean supports(Provider provider) {
        return adapters.stream().anyMatch(adapter -> adapter.provider() == provider);
    }

    public List<ImportedEntry> parse(Provider provider, byte[] content) {
        CsvImportAdapter adapter = adapters.stream()
                .filter(candidate -> candidate.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new ImportNotSupportedException("No CSV import for " + provider));

        List<ImportedEntry> entries = adapter.parse(CsvTable.parse(decode(content)));
        log.debug("Read {} rows from an uploaded {} export", entries.size(), provider);

        if (entries.isEmpty()) {
            throw new CsvFormatException("That file has the right columns but no rows this import could read.");
        }
        return entries;
    }

    /**
     * UTF-8 where the file is UTF-8, and Windows-1252 where it is not.
     *
     * <p>Exports opened and re-saved in a spreadsheet on Windows come back in the local
     * codepage, and decoding those as UTF-8 does not fail loudly — it silently replaces every
     * accented character, so a title quietly stops matching. Strict decoding is what turns
     * that into a decision rather than a corruption.
     */
    private String decode(byte[] content) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            log.debug("Upload is not UTF-8; reading it as Windows-1252");
            return new String(content, java.nio.charset.Charset.forName("windows-1252"));
        }
    }
}
