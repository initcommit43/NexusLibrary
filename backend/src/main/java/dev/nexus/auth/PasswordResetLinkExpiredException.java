package dev.nexus.auth;

/** The presented link is unknown, already spent, or past its half hour — the three are not told apart. */
public class PasswordResetLinkExpiredException extends RuntimeException {

    public PasswordResetLinkExpiredException() {
        super("That reset link has expired or has already been used. Please ask for a new one.");
    }
}
