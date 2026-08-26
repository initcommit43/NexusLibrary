package dev.nexus.core.catalog;

import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.MetadataAdapterRegistry;
import dev.nexus.core.domain.MediaType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serves browse shelves, and holds them in memory so a page view does not cost a request.
 *
 * <p>The cache is the point of this class. A browse page is four shelves, IGDB permits four
 * requests a second, and every reader opening the page would otherwise spend the whole budget
 * on identical answers — "Popular now" is the same list for everyone, unlike a search or a
 * library. One copy per shelf, shared by every reader, refreshed on a timer.
 *
 * <p>Deliberately not the database cache in {@code core.cache}: that one persists items
 * someone tracked, and browse results are transient by the same rule search results are.
 * A title appearing on a shelf is not a reason to write it down.
 */
@Service
public class BrowseService {

    /** What one shelf holds. Wider than a page shows, so the client can lay it out freely. */
    private static final int SHELF_SIZE = 20;

    private static final Logger log = LoggerFactory.getLogger(BrowseService.class);

    private record CacheKey(MediaType mediaType, String shelfId) {}

    private record CachedShelf(List<ItemSearchResult> results, Instant fetchedAt) {}

    private final MetadataAdapterRegistry adapters;
    private final Duration ttl;
    private final Map<CacheKey, CachedShelf> cache = new ConcurrentHashMap<>();

    public BrowseService(MetadataAdapterRegistry adapters, BrowseProperties properties) {
        this.adapters = adapters;
        this.ttl = properties.ttl();
    }

    /** What this media type can show, straight from its adapter. Never cached: it is a constant. */
    public List<BrowseShelf> shelves(MediaType mediaType) {
        return adapters.requireForMediaType(mediaType).browseShelves(mediaType);
    }

    /**
     * One shelf, from cache when it is still fresh.
     *
     * <p>A stale copy is kept and returned if the source is down. A browse page is discovery
     * rather than data anyone depends on being current, and yesterday's popular games are a
     * better answer than an error page.
     */
    public List<ItemSearchResult> shelf(MediaType mediaType, String shelfId) {
        CacheKey key = new CacheKey(mediaType, shelfId);
        CachedShelf cached = cache.get(key);

        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(ttl) < 0) {
            return cached.results();
        }

        MetadataAdapter adapter = adapters.requireForMediaType(mediaType);
        try {
            List<ItemSearchResult> fresh = adapter.browse(mediaType, shelfId, SHELF_SIZE);
            cache.put(key, new CachedShelf(fresh, Instant.now()));
            return fresh;
        } catch (RuntimeException e) {
            if (cached == null) {
                throw e;
            }
            log.debug("Serving a stale {} shelf for {}: {}", shelfId, mediaType, e.toString());
            return cached.results();
        }
    }

    /** Drops every cached shelf. Exists for tests and for a future manual refresh. */
    public void clear() {
        cache.clear();
    }
}
