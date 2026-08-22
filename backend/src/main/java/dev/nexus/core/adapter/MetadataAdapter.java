package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.List;
import java.util.Optional;

/**
 * Implemented per module, consumed by core. Adapters are dumb producers: they translate
 * one external API and never touch the database, so resolution and caching stay in one
 * place regardless of how many modules exist.
 */
public interface MetadataAdapter {

    MediaType mediaType();

    Source source();

    List<ItemSearchResult> search(String query, int limit);

    Optional<TrackableItemData> fetchById(String externalId);
}
