package dev.nexus.core.web;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RateLimiterCleanupTask {

    private final RateLimiter rateLimiter;

    public RateLimiterCleanupTask(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Scheduled(fixedDelay = 300_000)
    public void evictExpiredWindows() {
        rateLimiter.evictExpired();
    }
}
