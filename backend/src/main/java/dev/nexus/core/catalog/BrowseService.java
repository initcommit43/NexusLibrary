package dev.nexus.core.catalog;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
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

    /** What one shelf row holds. Enough to scroll through without asking for more. */
    private static final int SHELF_SIZE = 24;

    /** What one page of a "view all" grid holds. */
    public static final int PAGE_SIZE = 40;

    private static final Logger log = LoggerFactory.getLogger(BrowseService.class);

    /**
     * The size is part of the key. A shelf row asks for 24 and a grid asks for 40, and serving
     * one from the other's copy would either truncate the grid or, worse, leave a gap: page two
     * starts where a page of 40 ended, not where a row of 24 did.
     */
    private record CacheKey(MediaType mediaType, String shelfId, int size) {}

    private record CachedShelf(BrowseResults results, Instant fetchedAt) {}

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
    public BrowseResults shelf(MediaType mediaType, String shelfId) {
        return firstPage(mediaType, shelfId, SHELF_SIZE);
    }

    /**
     * One page of one shelf, for the grid behind "view all".
     *
     * <p>Only the first page is cached. It is what every reader lands on and is worth holding;
     * page seven is one reader scrolling, and keeping every page anyone ever asks for would
     * grow without bound for no shared benefit.
     */
    public BrowseResults page(MediaType mediaType, String shelfId, int page) {
        if (page <= 1) {
            return firstPage(mediaType, shelfId, PAGE_SIZE);
        }
        return adapters.requireForMediaType(mediaType).browse(mediaType, shelfId, page, PAGE_SIZE);
    }

    /**
     * The cached path. A stale copy is kept and returned if the source is down: a browse page
     * is discovery rather than data anyone depends on being current, and yesterday's popular
     * titles are a better answer than an error page.
     */
    private BrowseResults firstPage(MediaType mediaType, String shelfId, int size) {
        CacheKey key = new CacheKey(mediaType, shelfId, size);
        CachedShelf cached = cache.get(key);

        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(ttl) < 0) {
            return cached.results();
        }

        MetadataAdapter adapter = adapters.requireForMediaType(mediaType);
        try {
            BrowseResults fresh = adapter.browse(mediaType, shelfId, 1, size);
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
