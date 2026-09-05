package dev.nexus.auth;

import java.util.Locale;

/**
 * How an address is written down, in one place.
 *
 * <p>An account is stored under its normalised address, so every lookup has to normalise it
 * the same way. Two copies of these two calls are two chances for a reset request to find
 * nobody where signing in finds an account.
 */
public final class EmailAddresses {

    private EmailAddresses() {}

    public static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
