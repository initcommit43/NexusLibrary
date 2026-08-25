package dev.nexus.core.importing;

import dev.nexus.core.adapter.ImportedEntry;
import dev.nexus.core.domain.Provider;
import java.util.List;

/**
 * Reads one provider's exported CSV into the same {@link ImportedEntry} rows its live
 * adapter produces - the alternative route in for a service whose API is unavailable,
 * paywalled, or simply not worth connecting for a one-off.
 *
 * <p>Implemented per module for the same reason {@link dev.nexus.core.adapter.LibraryImportAdapter}
 * is: which column holds a MyAnimeList id, and what that provider calls "on hold", are facts
 * about that provider and not about importing. Core owns the parse and the pipeline.
 *
 * <p>Crucially the rows carry the same hints the live adapter sets, so the provider's
 * existing resolver matches them unchanged. A CSV import resolves exactly as well as the
 * API import does, and lands in the same unmatched report when it cannot.
 */
public interface CsvImportAdapter {

    Provider provider();

    /**
     * @throws CsvFormatException when the file has none of the columns this provider needs,
     *     which is the difference between an empty library and the wrong file
     */
    List<ImportedEntry> parse(CsvTable table);
}
