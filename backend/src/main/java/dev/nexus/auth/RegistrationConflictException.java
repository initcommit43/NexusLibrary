package dev.nexus.auth;

import java.util.Map;

/** Email or username already taken. Carries the offending field for inline form display. */
public class RegistrationConflictException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    public RegistrationConflictException(String field, String message) {
        super(message);
        this.fieldErrors = Map.of(field, message);
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
