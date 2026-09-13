package dev.nexus.core.adapter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One of a source's option lists for the filter bar — genres, tags, platforms — fetched once
 * and kept for the run.
 *
 * <p>They are the same answer for every reader and change about never, so the first good one
 * is kept for good. A failed or empty answer is kept too, for a while: the filter bar cannot
 * draw until its lists are answered, and each attempt at a source that is down queues behind
 * that source's rate limit and its retries. Asking again on every visit made every visit wait
 * through all of that for the same failure.
 *
 * <p>One fetch at a time per list. Anime and manga warm at startup side by side and share one
 * AniList genre list, and a second caller waiting on the first answer costs nothing, where a
 * second request would take a slot from the shelves queued behind it.
 */
public final class HeldOptions<T> {

    /** Long enough that a run of visits costs one attempt, short enough that an outage heals on its own. */
    public static final Duration RETRY_AFTER = Duration.ofMinutes(5);

    private final Supplier<Instant> clock;

    private volatile List<T> held;

    /** Guarded by this. */
    private Instant failedAt;

    public HeldOptions() {
        this(Instant::now);
    }

    public HeldOptions(Supplier<Instant> clock) {
        this.clock = clock;
    }

    /**
     * The kept list; or, with none kept and no recent failure, a fresh fetch.
     *
     * @param fallback what a reader is given while the source has nothing to give
     * @param onFailure told of a fetch that threw, so each source can say so in its own words
     */
    public List<T> get(Supplier<List<T>> fetch, List<T> fallback, Consumer<RuntimeException> onFailure) {
        List<T> known = held;
        if (known != null) {
            return known;
        }

        synchronized (this) {
            if (held != null) {
                return held;
            }

            Instant now = clock.get();
            if (failedAt != null && now.isBefore(failedAt.plus(RETRY_AFTER))) {
                return fallback;
            }

            try {
                List<T> fetched = fetch.get();
                if (!fetched.isEmpty()) {
                    held = List.copyOf(fetched);
                    failedAt = null;
                    return held;
                }
            } catch (RuntimeException e) {
                onFailure.accept(e);
            }

            failedAt = now;
            return fallback;
        }
    }
}
