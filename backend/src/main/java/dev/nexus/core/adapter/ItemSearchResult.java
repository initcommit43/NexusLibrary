package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;

/**
 * A lightweight search hit. Deliberately not persisted: an item enters the cache when
 * someone actually tracks it, not because it appeared in someone's search results.
 */
public record ItemSearchResult(
        MediaType mediaType,
        Source source,
        String externalId,
        String title,
        String coverUrl,
        LocalDate releaseDate) {}
