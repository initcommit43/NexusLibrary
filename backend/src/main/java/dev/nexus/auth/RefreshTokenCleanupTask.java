package dev.nexus.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCleanupTask {

    private static final long ONE_DAY_MS = 86_400_000;

    private final RefreshTokenService refreshTokens;

    public RefreshTokenCleanupTask(RefreshTokenService refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /** Daily is often enough: an expired row admits nothing, it only takes up space. */
    @Scheduled(fixedDelay = ONE_DAY_MS)
    public void pruneExpiredTokens() {
        refreshTokens.pruneExpired();
    }
}
