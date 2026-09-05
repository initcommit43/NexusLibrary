package dev.nexus.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetCleanupTask {

    private static final long ONE_DAY_MS = 86_400_000;

    private final PasswordResetService passwordResets;

    public PasswordResetCleanupTask(PasswordResetService passwordResets) {
        this.passwordResets = passwordResets;
    }

    /** Half-hour links, swept daily: an expired row admits nothing, it only takes up space. */
    @Scheduled(fixedDelay = ONE_DAY_MS)
    public void pruneExpiredLinks() {
        passwordResets.pruneExpired();
    }
}
