package dev.nexus.core.account;

/** A date of birth is given once. A 409, since the account already holds one. */
public class DateOfBirthAlreadySetException extends RuntimeException {

    public DateOfBirthAlreadySetException() {
        super("Your date of birth is already set. Contact us if it is wrong.");
    }
}
