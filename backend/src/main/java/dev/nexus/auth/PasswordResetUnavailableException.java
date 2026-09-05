package dev.nexus.auth;

/** The deployment has no way to send the link, so there is no point issuing one. */
public class PasswordResetUnavailableException extends RuntimeException {

    public PasswordResetUnavailableException() {
        super("Password reset is not available on this deployment yet.");
    }
}
