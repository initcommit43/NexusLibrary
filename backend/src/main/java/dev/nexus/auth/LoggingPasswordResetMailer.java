package dev.nexus.auth;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A stand-in for the sender that has not been chosen yet: it writes the link to the log so
 * the flow can be walked end to end locally.
 *
 * <p>Never outside dev and test. A reset link in a log file is a live credential in a log
 * file, which is exactly what plan.md §13 forbids — so production has no mailer at all and
 * says so ({@link PasswordResetUnavailableException}) rather than quietly logging one.
 * Replacing this with a real sender is one class implementing {@link PasswordResetMailer}
 * and dropping the profile restriction.
 *
 * <p>The profiles are named rather than excluded. {@code !prod} reads the same until you
 * notice what it does when no profile is set at all: the Dockerfile sets none, so the built
 * image matched it and logged live reset links to the platform's log store.
 */
@Component
@Profile({"dev", "test"})
public class LoggingPasswordResetMailer implements PasswordResetMailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailer.class);

    @Override
    public void send(AppUser recipient, String resetLink, Duration validFor) {
        log.info(
                "No password reset mailer is configured, so the link was not sent. "
                        + "Open it yourself, good for {} minutes: {}",
                validFor.toMinutes(),
                resetLink);
    }
}
