package dev.nexus.core.cache;

import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The global shared cache. An external API is called once per unique item, ever; every
 * later lookup by any user is a local read. API traffic therefore scales with the number
 * of distinct titles tracked, not with the number of users.
 */
@Service
public class ItemCacheService {

    private static final Logger log = LoggerFactory.getLogger(ItemCacheService.class);

    private final TrackableItemRepository items;
    private final MetadataAdapterRegistry adapters;
    private final TrackableItemWriter writer;

    public ItemCacheService(
            TrackableItemRepository items, MetadataAdapterRegistry adapters, TrackableItemWriter writer) {
        this.items = items;
        this.adapters = adapters;
        this.writer = writer;
    }

    /** Returns the cached item, fetching and storing it first if this is its first sighting. */
    public TrackableItem findOrCache(Source source, String externalId) {
        return items.findBySourceAndExternalId(source, externalId)
                .orElseGet(() -> fetchAndStore(source, externalId));
    }

    private TrackableItem fetchAndStore(Source source, String externalId) {
        MetadataAdapter adapter = adapters
                .forSource(source)
                .orElseThrow(() -> new ItemNotFoundException("No adapter registered for source " + source));

        TrackableItemData data = adapter.fetchById(externalId)
                .orElseThrow(() -> new ItemNotFoundException("No item " + externalId + " in " + source));

        try {
            return writer.insert(data);
        } catch (DataIntegrityViolationException e) {
            // Another request cached the same item between our lookup and this insert. The
            // unique constraint is the arbiter; read the row that won rather than failing.
            log.debug("Lost cache insert race for {}:{}, reading the winning row", source, externalId);
            return items.findBySourceAndExternalId(source, externalId).orElseThrow(() -> e);
        }
    }
}
