package dev.nexus.auth;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sweeps spent and expired confirmation links, which nothing did until now.
 *
 * <p>{@link EmailVerificationService#pruneExpired()} was written alongside the reset flow's
 * and then never called — there was a task for reset links and one for refresh tokens, and
 * none for these. A 48-hour link that is never deleted is personal data kept indefinitely for
 * a purpose that ended two days in, which is what Art. 5(1)(e) GDPR is about, and it made the
 * retention the privacy policy states impossible to say truthfully.
 */
@Component
public class EmailVerificationCleanupTask {

    private static final long ONE_DAY_MS = 86_400_000;

    private final EmailVerificationService verification;

    public EmailVerificationCleanupTask(EmailVerificationService verification) {
        this.verification = verification;
    }

    /** Two-day links, swept daily: an expired row admits nothing, it only takes up space. */
    @Scheduled(fixedDelay = ONE_DAY_MS)
    public void pruneExpiredLinks() {
        verification.pruneExpired();
    }
}
