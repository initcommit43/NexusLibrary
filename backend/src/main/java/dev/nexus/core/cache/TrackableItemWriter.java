package dev.nexus.core.cache;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the cache insert in a transaction of its own.
 *
 * <p>Separate bean on purpose: a losing insert race must roll back in isolation. Attempting
 * it inline would leave the caller's persistence context holding a failed entity, and the
 * recovery read would then fail instead of returning the row that won.
 */
@Component
public class TrackableItemWriter {

    private final TrackableItemRepository items;

    public TrackableItemWriter(TrackableItemRepository items) {
        this.items = items;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrackableItem insert(TrackableItemData data) {
        return items.saveAndFlush(new TrackableItem(
                data.mediaType(),
                data.source(),
                data.externalId(),
                data.title(),
                data.coverUrl(),
                data.releaseDate(),
                data.itemState(),
                data.metadata()));
    }
}
