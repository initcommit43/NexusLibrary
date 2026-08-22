package dev.nexus.modules.games;

import java.util.concurrent.TimeUnit;

/**
 * Blocking token bucket for calls we make out to IGDB, which permits 4 requests per second.
 *
 * <p>Distinct from {@code core.web.RateLimiter}, which rejects excess inbound requests. Here
 * rejecting would turn a busy moment into a user-visible failure, so callers wait instead.
 */
public class OutboundRateLimiter {

    private final long spacingNanos;
    private long nextPermitAt;

    public OutboundRateLimiter(int requestsPerSecond) {
        this.spacingNanos = TimeUnit.SECONDS.toNanos(1) / requestsPerSecond;
        this.nextPermitAt = System.nanoTime();
    }

    public void acquire() {
        long waitNanos;

        synchronized (this) {
            long now = System.nanoTime();
            long permitAt = Math.max(now, nextPermitAt);
            nextPermitAt = permitAt + spacingNanos;
            waitNanos = permitAt - now;
        }

        if (waitNanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for an IGDB request slot", e);
        }
    }
}
