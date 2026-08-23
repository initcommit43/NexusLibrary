package dev.nexus.core.cache;

import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /** Bulk insert for imports, where losing a race on one item must not fail the batch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<TrackableItem> insertAll(List<TrackableItemData> data) {
        return items.saveAll(data.stream().map(TrackableItemWriter::toEntity).toList());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TrackableItem insert(TrackableItemData data) {
        return items.saveAndFlush(toEntity(data));
    }

    /**
     * Writes freshly fetched copies over the rows already cached. Runs on a background
     * thread, hence its own transaction — the read that triggered it has long since
     * committed.
     *
     * <p>An id the source no longer returns keeps the copy already cached: a title being
     * pulled from IGDB must not empty the shelves of everyone tracking it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refreshAll(Source source, List<TrackableItemData> refreshed) {
        Map<String, TrackableItemData> byExternalId = refreshed.stream()
                .collect(Collectors.toMap(TrackableItemData::externalId, Function.identity(), (a, b) -> a));
        if (byExternalId.isEmpty()) {
            return;
        }

        Instant now = Instant.now();
        for (TrackableItem item : items.findBySourceAndExternalIdIn(source, byExternalId.keySet())) {
            TrackableItemData data = byExternalId.get(item.getExternalId());
            item.refreshFrom(
                    data.title(), data.coverUrl(), data.releaseDate(), data.itemState(), data.metadata(), now);
            items.save(item);
        }
    }

    private static TrackableItem toEntity(TrackableItemData data) {
        return new TrackableItem(
                data.mediaType(),
                data.source(),
                data.externalId(),
                data.title(),
                data.coverUrl(),
                data.releaseDate(),
                data.itemState(),
                data.metadata());
    }
}
