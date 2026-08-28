package dev.nexus.core.account;

/** A token outliving the account it names — a deleted account whose session has not expired. */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(long userId) {
        super("No account " + userId);
    }
}
