package dev.nexus.auth;

/** Raised when a deployment has stopped taking new accounts. */
public class RegistrationClosedException extends RuntimeException {

    public RegistrationClosedException() {
        super("This app is not taking new accounts.");
    }
}
