package dev.nexus.auth;

import java.time.Duration;

/**
 * How a reset link reaches the person who asked for it.
 *
 * <p>An interface with one caller, because the sender is the part of this flow that is not
 * decided yet: nothing in the app sends mail today. Everything around it — the token, its
 * half hour, the single use, the sessions it ends — is finished and testable without knowing
 * whether the link eventually leaves over SMTP or a provider's API.
 */
public interface PasswordResetMailer {

    /**
     * @param validFor how long the link works, so the mail can say so. The reader needs to
     *     know whether the one in yesterday's inbox is worth clicking.
     */
    void send(AppUser recipient, String resetLink, Duration validFor);
}
