package dev.nexus.core.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Writes the mail to the log instead of sending it, so a flow can be walked end to end with
 * no provider account.
 *
 * <p>Dev and test only, and the profiles are named rather than excluded for the reason
 * {@link dev.nexus.auth.LoggingPasswordResetMailer} records: {@code !prod} also matches a
 * process started with no profile at all, which is what the Dockerfile does. A verification
 * link in a production log is a working link in a log.
 *
 * <p>Production with no key configured therefore has no {@link Mailer} at all. That is
 * deliberate — a flow that cannot mail should fail where it is called rather than report
 * success for a message nobody will ever receive.
 */
@Component
@Profile({"dev", "test"})
@Conditional(ResendKeyConfigured.Missing.class)
public class LoggingMailer implements Mailer {

    private static final Logger log = LoggerFactory.getLogger(LoggingMailer.class);

    @Override
    public void send(String to, String subject, String html) {
        log.info("No mailer is configured, so nothing was sent. Subject: {}\n{}", subject, html);
    }
}
