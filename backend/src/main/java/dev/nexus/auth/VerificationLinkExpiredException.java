package dev.nexus.auth;

/**
 * Unknown, spent or expired. One message for all three: which it was is not the caller's
 * business, and the reader's next step is the same either way — ask for another link.
 */
public class VerificationLinkExpiredException extends RuntimeException {

    public VerificationLinkExpiredException() {
        super("That confirmation link has expired. Please request a new one.");
    }
}
