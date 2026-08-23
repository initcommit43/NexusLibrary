package dev.nexus.core.catalog;

import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.cache.ItemCacheService;
import dev.nexus.core.cache.ItemNotFoundException;
import dev.nexus.core.cache.TrackableItemWriter;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * A catalogue item as a page of its own, whether or not anyone tracks it.
 *
 * <p>That distinction is what makes relations work: a sequel you have never added is still
 * somewhere you can go, and adding it is an action on that page rather than a precondition
 * for seeing it.
 */
@Service
public class MediaDetailService {

    /** Where a source's own extra detail lives on the shared item. */
    public static final String DETAIL_KEY = "detail";

    private static final Logger log = LoggerFactory.getLogger(MediaDetailService.class);

    private final ItemCacheService cache;
    private final MetadataAdapterRegistry adapters;
    private final TrackableItemWriter writer;

    public MediaDetailService(
            ItemCacheService cache, MetadataAdapterRegistry adapters, TrackableItemWriter writer) {
        this.cache = cache;
        this.adapters = adapters;
        this.writer = writer;
    }

    /**
     * The cached item, with the source's detail attached the first time anyone looks.
     *
     * <p>Detail is fetched once per title and then shared by everyone, the same bargain the
     * item cache itself makes. A source that has nothing extra to say simply has none.
     */
    public TrackableItem findOrFetch(Source source, String externalId) {
        TrackableItem item = cache.findOrCache(source, externalId);
        if (item.getMetadata().containsKey(DETAIL_KEY)) {
            return item;
        }

        Map<String, Object> detail = adapters
                .forSource(source)
                .flatMap(adapter -> adapter.fetchDetail(externalId))
                .orElse(Map.of());

        if (detail.isEmpty()) {
            return item;
        }

        try {
            writer.storeDetail(source, externalId, detail);
            item.getMetadata().put(DETAIL_KEY, detail);
        } catch (RuntimeException e) {
            // The page is perfectly readable without it; losing the write is not worth failing on.
            log.warn("Could not store detail for {}:{}", source, externalId, e);
        }
        return item;
    }

    public TrackableItem require(Source source, String externalId) {
        TrackableItem item = findOrFetch(source, externalId);
        if (item == null) {
            throw new ItemNotFoundException("No item " + externalId + " in " + source);
        }
        return item;
    }
}
