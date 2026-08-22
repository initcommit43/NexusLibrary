package dev.nexus.core.adapter;

import dev.nexus.core.domain.ExternalAccount;
import dev.nexus.core.domain.Provider;
import java.util.List;

/**
 * Pulls a user's whole library from one provider. Like {@link MetadataAdapter}, an
 * implementation only translates an external API: it resolves nothing and writes nothing.
 */
public interface LibraryImportAdapter {

    Provider provider();

    List<ImportedEntry> pullLibrary(ExternalAccount account);
}
