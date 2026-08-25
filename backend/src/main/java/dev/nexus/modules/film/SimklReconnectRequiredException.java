package dev.nexus.modules.film;

import dev.nexus.core.importing.UserFixableException;

/**
 * The stored Simkl token was refused. Simkl tokens do not expire on a clock the way MAL and
 * Trakt ones do - they last until the reader revokes the app - so this means revoked, and
 * only approving again can mend it.
 */
public class SimklReconnectRequiredException extends RuntimeException implements UserFixableException {

    public SimklReconnectRequiredException() {
        super("The Simkl link is no longer valid.");
    }

    @Override
    public String advice() {
        return "The Simkl link is no longer valid. Reconnect Simkl in settings, then try again.";
    }
}
