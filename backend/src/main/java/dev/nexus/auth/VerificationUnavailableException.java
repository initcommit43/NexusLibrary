package dev.nexus.auth;

/** This deployment has no mailer, so no link can be issued. A 501, not a rejection. */
public class VerificationUnavailableException extends RuntimeException {

    public VerificationUnavailableException() {
        super("Email verification is not available on this deployment.");
    }
}
