package dev.nexus.auth;

import java.util.Map;

/**
 * The address cannot open an account: malformed, a throwaway service, or a domain that
 * receives no mail.
 *
 * <p>Separate from {@link RegistrationConflictException} because it is a different answer. That
 * one means the address is fine and already spoken for, and says so with a 409. This one means
 * the address itself will not do, which is a 400 — nothing about it would work on a retry.
 *
 * <p>Carries the field so the form can say it under the box the reader typed into.
 */
public class EmailNotAcceptableException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public EmailNotAcceptableException(String message) {
        super(message);
        this.fieldErrors = Map.of("email", message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
