package dev.nexus.core.adapter;

/**
 * How a bulk fetch reports its way through a long batch.
 *
 * <p>Caching several hundred unseen titles is the slowest stretch of an import — one call
 * per batch, paced against someone else's rate limit — and it happens far below the job
 * that wants to report it. A callback is what carries a count up through the cache without
 * teaching the cache what a job is.
 */
@FunctionalInterface
public interface FetchProgress {

    /** For every caller that has nothing to report to. */
    FetchProgress IGNORED = (fetched, total) -> {};

    void report(int fetched, int total);
}
