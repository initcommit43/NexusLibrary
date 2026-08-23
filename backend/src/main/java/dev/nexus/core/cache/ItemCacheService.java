package dev.nexus.core.cache;

import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import dev.nexus.core.domain.TrackableItemRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
    private final ItemRefreshService refresh;

    public ItemCacheService(
            TrackableItemRepository items,
            MetadataAdapterRegistry adapters,
            TrackableItemWriter writer,
            ItemRefreshService refresh) {
        this.items = items;
        this.adapters = adapters;
        this.writer = writer;
        this.refresh = refresh;
    }

    /** Returns the cached item, fetching and storing it first if this is its first sighting. */
    public TrackableItem findOrCache(Source source, String externalId) {
        return items.findBySourceAndExternalId(source, externalId)
                .map(this::serveAndRefreshIfStale)
                .orElseGet(() -> fetchAndStore(source, externalId));
    }

    /**
     * Cache-on-miss for a whole batch: one query for what is already cached, one bulk fetch
     * for the rest. An import of several hundred titles therefore costs a couple of external
     * calls rather than one per item — and costs none at all where another user already
     * caused the same titles to be cached.
     *
     * @return the items that resolved, keyed by external id; ids the source did not return
     *     are simply absent
     */
    public Map<String, TrackableItem> findOrCacheAll(Source source, Collection<String> externalIds) {
        if (externalIds.isEmpty()) {
            return Map.of();
        }

        Map<String, TrackableItem> found = items.findBySourceAndExternalIdIn(source, externalIds).stream()
                .collect(Collectors.toMap(TrackableItem::getExternalId, Function.identity(), (a, b) -> a));
        refresh.refreshIfStale(found.values());

        List<String> missing =
                externalIds.stream().distinct().filter(id -> !found.containsKey(id)).toList();
        if (missing.isEmpty()) {
            return found;
        }

        MetadataAdapter adapter = adapters
                .forSource(source)
                .orElseThrow(() -> new ItemNotFoundException("No adapter registered for source " + source));

        List<TrackableItemData> fetched = adapter.fetchByIds(missing);

        Map<String, TrackableItem> result = new LinkedHashMap<>(found);
        try {
            writer.insertAll(fetched).forEach(item -> result.put(item.getExternalId(), item));
        } catch (DataIntegrityViolationException e) {
            // Another import cached some of these first. Re-read the whole batch rather than
            // unpicking which rows collided.
            log.debug("Lost a batch cache race for {}, re-reading", source);
            items.findBySourceAndExternalIdIn(source, externalIds)
                    .forEach(item -> result.put(item.getExternalId(), item));
        }
        return result;
    }

    /** The cached copy goes back to the caller untouched; any re-fetch happens behind it. */
    private TrackableItem serveAndRefreshIfStale(TrackableItem item) {
        refresh.refreshIfStale(item);
        return item;
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
