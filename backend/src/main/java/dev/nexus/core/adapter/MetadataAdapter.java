package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Optional;

/**
 * Implemented per module, consumed by core. Adapters are dumb producers: they translate
 * one external API and never touch the database, so resolution and caching stay in one
 * place regardless of how many modules exist.
 */
public interface MetadataAdapter {

    /**
     * The media types this adapter serves. Usually one, but a source can be canonical for
     * several: AniList covers anime and manga, TMDB covers films and shows.
     */
    Set<MediaType> mediaTypes();

    Source source();

    List<ItemSearchResult> search(MediaType mediaType, String query, int limit);

    Optional<TrackableItemData> fetchById(String externalId);

    /**
     * Fetches many items at once. Importing a library needs hundreds of items, and one
     * request each would spend minutes inside the source's rate limit.
     *
     * <p>The default is a correct-but-slow loop so a module can ignore this until it has a
     * reason to care; sources with a bulk endpoint should override it.
     */
    default List<TrackableItemData> fetchByIds(Collection<String> externalIds) {
        return externalIds.stream().map(this::fetchById).flatMap(Optional::stream).toList();
    }
}
