package dev.nexus.auth;

/**
 * The password was right and the address has not been confirmed.
 *
 * <p>Separate from {@link AuthenticationFailedException} because the reader needs a different
 * thing: not "check your details" but "check your inbox", plus a way to ask for another link.
 * It is only ever raised after the password has already been verified, so it says nothing to
 * anyone who could not already sign in.
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Please confirm your email address before signing in.");
    }
}
