package dev.nexus.modules.film;

import dev.nexus.core.importing.UserFixableException;

/**
 * The stored Trakt tokens are beyond saving — expired with no refresh token, or refused on
 * refresh. A Trakt token lasts three months, so this is a link left alone for a season
 * rather than a rare accident.
 */
public class TraktReconnectRequiredException extends RuntimeException implements UserFixableException {

    public TraktReconnectRequiredException() {
        super("The Trakt link has expired.");
    }

    @Override
    public String advice() {
        return "The Trakt link has expired. Reconnect Trakt in settings, then try again.";
    }
}
