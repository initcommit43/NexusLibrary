package dev.nexus.core.web;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counter, in memory and therefore per instance. That is deliberate for a
 * single-instance deployment; a horizontally scaled one needs a shared store (Redis)
 * instead, since each replica would otherwise grant the full quota on its own.
 */
@Component
public class RateLimiter {

    private record Window(Instant startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Duration windowLength = Duration.ofMinutes(1);

    public void check(String key, int limit) {
        Instant now = Instant.now();

        Window window = windows.compute(key, (ignored, existing) -> existing == null
                        || existing.startedAt().plus(windowLength).isBefore(now)
                ? new Window(now, new AtomicInteger())
                : existing);

        if (window.count().incrementAndGet() > limit) {
            throw new RateLimitExceededException();
        }
    }

    /** Keeps the map from growing without bound as client keys churn. */
    public void evictExpired() {
        Instant cutoff = Instant.now().minus(windowLength);
        windows.entrySet().removeIf(entry -> entry.getValue().startedAt().isBefore(cutoff));
    }
}
