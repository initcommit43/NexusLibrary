package dev.nexus.core.cache;

import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.TrackableItem;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Decides whether a cached copy is old enough to be worth an external call. */
@Component
public class StalenessPolicy {

    private final RefreshProperties properties;

    public StalenessPolicy(RefreshProperties properties) {
        this.properties = properties;
    }

    public boolean isStale(TrackableItem item, Instant now) {
        return isStale(item.getItemState(), item.lastRefreshedAt(), now);
    }

    /**
     * A released item has no TTL at all — its title, cover and release date are settled, so
     * re-fetching it would spend API budget to write back what is already stored. Items only
     * ever move towards RELEASED, which makes refreshing an upcoming one self-limiting: the
     * refresh that finds it released is the last one it ever gets.
     */
    public boolean isStale(ItemState state, Instant lastRefreshed, Instant now) {
        // Only a row inserted in this very transaction has no timestamp read back yet, and
        // it was fetched moments ago.
        if (lastRefreshed == null) {
            return false;
        }
        return ttlFor(state).map(ttl -> lastRefreshed.isBefore(now.minus(ttl))).orElse(false);
    }

    private Optional<Duration> ttlFor(ItemState state) {
        return switch (state) {
            case RELEASED -> Optional.empty();
            case ONGOING -> Optional.of(properties.ongoingTtl());
            case UPCOMING -> Optional.of(properties.upcomingTtl());
        };
    }
}
