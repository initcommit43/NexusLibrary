package dev.nexus.auth;

import java.util.Map;

/**
 * A date of birth this service will not take: too young to register, or too far back to be a
 * real one. A 400 carrying the field, so the form says it under the date box.
 */
public class DateOfBirthRejectedException extends RuntimeException {

    private final transient Map<String, String> fieldErrors;

    private DateOfBirthRejectedException(String message) {
        super(message);
        this.fieldErrors = Map.of("dateOfBirth", message);
    }

    /** Names the age, so a reader turned away knows whether and when to come back. */
    public static DateOfBirthRejectedException tooYoung() {
        return new DateOfBirthRejectedException(
                "You must be at least " + AgePolicy.MINIMUM + " to create an account.");
    }

    public static DateOfBirthRejectedException implausible() {
        return new DateOfBirthRejectedException("Please check the date you were born.");
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}
