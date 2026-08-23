package dev.nexus.core.cache;

import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Re-fetches stale items on a background thread.
 *
 * <p>Separate from the service that decides what is stale: {@code @Async} is applied by a
 * proxy, so a method calling it on itself would run inline and block the very read it exists
 * to keep fast.
 */
@Component
public class ItemRefreshRunner {

    private static final Logger log = LoggerFactory.getLogger(ItemRefreshRunner.class);

    private final TrackableItemRepository items;
    private final MetadataAdapterRegistry adapters;
    private final TrackableItemWriter writer;

    public ItemRefreshRunner(
            TrackableItemRepository items, MetadataAdapterRegistry adapters, TrackableItemWriter writer) {
        this.items = items;
        this.adapters = adapters;
        this.writer = writer;
    }

    @Async
    public void refresh(Source source, List<Long> itemIds) {
        MetadataAdapter adapter = adapters.forSource(source).orElse(null);
        if (adapter == null) {
            return;
        }

        try {
            List<String> externalIds =
                    items.findAllById(itemIds).stream().map(TrackableItem::getExternalId).toList();
            writer.refreshAll(source, adapter.fetchByIds(externalIds));
        } catch (RuntimeException e) {
            // Best-effort by design: the cached copy is already serving reads, so a source
            // that is down or throttling costs nothing but freshness.
            log.warn("Could not refresh {} item(s) from {}", itemIds.size(), source, e);
        }
    }
}
