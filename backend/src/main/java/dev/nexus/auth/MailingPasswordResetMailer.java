package dev.nexus.auth;

import dev.nexus.core.mail.Mailer;
import dev.nexus.core.mail.MailerAvailable;
import java.time.Duration;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

/**
 * Sends a reset link through whatever {@link Mailer} this deployment has.
 *
 * <p>The reset flow was built against its own sender slot before any mail left this app, and
 * that slot only ever held a stand-in writing the link to the log. Filling it from the shared
 * mailer means reset and address confirmation leave by the same road, and a deployment that can
 * send one can send the other.
 *
 * <p>Exists only where a mailer does. {@link PasswordResetService} reads the absence of this bean
 * as "cannot reset" and says so before looking anyone up, which is what keeps the endpoint from
 * answering differently for a registered address than for a stranger's.
 */
@Component
@Conditional(MailerAvailable.class)
public class MailingPasswordResetMailer implements PasswordResetMailer {

    private final Mailer mailer;

    public MailingPasswordResetMailer(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void send(AppUser recipient, String resetLink, Duration validFor) {
        mailer.send(recipient.getEmail(), "Reset your NexusLibrary password", body(recipient, resetLink, validFor));
    }

    private static String body(AppUser recipient, String resetLink, Duration validFor) {
        return """
               <p>Hello %s,</p>
               <p>Someone asked to reset the password on your NexusLibrary account.</p>
               <p><a href="%s">Choose a new password</a></p>
               <p>The link works for %d minutes and only once. Using it signs you out everywhere \
               you are signed in.</p>
               <p>If you did not ask for this, ignore this message. Your password stays as it is.</p>
               """
                .formatted(escape(recipient.getUsername()), resetLink, validFor.toMinutes());
    }

    /** The username is the reader's own text going into HTML; it is not markup. */
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
