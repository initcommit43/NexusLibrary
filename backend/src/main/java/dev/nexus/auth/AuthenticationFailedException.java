package dev.nexus.auth;

/** Wrong credentials, or a token that is missing, expired or not valid for its purpose. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
