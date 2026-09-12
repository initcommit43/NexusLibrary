package dev.nexus.auth;

/** The resend button, held down. Protects the inbox on the other end as much as this server. */
public class TooManyVerificationLinksException extends RuntimeException {

    public TooManyVerificationLinksException() {
        super("Too many confirmation emails requested. Please wait a while before trying again.");
    }
}
