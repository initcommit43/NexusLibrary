package dev.nexus.core.adapter;

import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.Map;

/**
 * A fully resolved external item, ready for core to persist as a {@code TrackableItem}.
 *
 * @param adult what the source says about the title. An adapter reports it; who may see it is
 *     core's call. A source with no such notion says false.
 */
public record TrackableItemData(
        MediaType mediaType,
        Source source,
        String externalId,
        String title,
        String coverUrl,
        LocalDate releaseDate,
        ItemState itemState,
        boolean adult,
        Map<String, Object> metadata) {}
