package dev.nexus.core.cache;

import dev.nexus.core.domain.Source;
import dev.nexus.core.domain.TrackableItem;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Refresh-on-read, lazily: a read is always served from the cached copy, and a copy past its
 * TTL has a re-fetch started behind it. No read ever waits on an external API, so a slow or
 * unreachable source costs freshness rather than latency.
 */
@Service
public class ItemRefreshService {

    private static final long EVICT_INTERVAL_MS = 300_000;

    private final StalenessPolicy policy;
    private final ItemRefreshRunner runner;
    private final RefreshProperties properties;

    /**
     * When each item was last put up for refresh. In memory, and therefore per instance —
     * the same trade-off as the rate limiter and the job registry: a second instance would
     * duplicate some refreshes, which wastes calls but cannot corrupt anything.
     */
    private final Map<Long, Instant> attemptedAt = new ConcurrentHashMap<>();

    public ItemRefreshService(StalenessPolicy policy, ItemRefreshRunner runner, RefreshProperties properties) {
        this.policy = policy;
        this.runner = runner;
        this.properties = properties;
    }

    public void refreshIfStale(TrackableItem item) {
        refreshIfStale(List.of(item));
    }

    /** Grouped by source so a whole dashboard's worth of stale items costs one bulk call. */
    public void refreshIfStale(Collection<TrackableItem> items) {
        Instant now = Instant.now();
        Instant retryFrom = now.minus(properties.retryAfter());
        Map<Source, List<Long>> due = new LinkedHashMap<>();
        int remaining = properties.maxItemsPerRead();

        for (TrackableItem item : items) {
            if (remaining == 0) {
                break;
            }
            if (!policy.isStale(item, now) || !claim(item.getId(), now, retryFrom)) {
                continue;
            }
            due.computeIfAbsent(item.getSource(), source -> new ArrayList<>()).add(item.getId());
            remaining--;
        }

        due.forEach(runner::refresh);
    }

    /**
     * Claims the refresh of one item. Concurrent reads of the same title are the normal case
     * for a shared cache, and only one of them should turn into an external call: whichever
     * caller gets its own instant back from {@code compute} is the one holding the claim.
     */
    private boolean claim(Long itemId, Instant now, Instant retryFrom) {
        return attemptedAt.compute(itemId, (id, last) -> last != null && last.isAfter(retryFrom) ? last : now)
                == now;
    }

    /** An attempt older than the retry window can no longer suppress anything. */
    @Scheduled(fixedDelay = EVICT_INTERVAL_MS)
    public void evictOldAttempts() {
        Instant cutoff = Instant.now().minus(properties.retryAfter());
        attemptedAt.values().removeIf(attempt -> attempt.isBefore(cutoff));
    }
}
