package dev.nexus.core.account;

/** The password given to confirm a change was not the account's. */
public class PasswordMismatchException extends RuntimeException {

    public PasswordMismatchException() {
        super("That password is not correct.");
    }
}
