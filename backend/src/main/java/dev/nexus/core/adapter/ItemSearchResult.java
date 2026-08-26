package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.Map;

/**
 * A lightweight search hit. Deliberately not persisted: an item enters the cache when
 * someone actually tracks it, not because it appeared in someone's search results.
 *
 * @param facets what a ranked list shows beside the title — a score, a format, an episode
 *     count. Empty for a plain search hit, which needs none of it; a browse shelf that ranks
 *     its rows fills in whatever its source happens to know. Deliberately loose, for the same
 *     reason {@link TrackableItemData}'s metadata is: only the module's own UI reads it.
 */
public record ItemSearchResult(
        MediaType mediaType,
        Source source,
        String externalId,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        Map<String, Object> facets) {

    public ItemSearchResult(
            MediaType mediaType,
            Source source,
            String externalId,
            String title,
            String coverUrl,
            LocalDate releaseDate) {
        this(mediaType, source, externalId, title, coverUrl, releaseDate, Map.of());
    }
}
