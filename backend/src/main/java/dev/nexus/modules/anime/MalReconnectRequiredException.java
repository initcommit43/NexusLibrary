package dev.nexus.modules.anime;

import dev.nexus.core.importing.UserFixableException;

/**
 * The stored MAL tokens are beyond saving — expired with no refresh token, or refused on
 * refresh. Only going through the approval again can mint new ones, and only the reader
 * can do that.
 */
public class MalReconnectRequiredException extends RuntimeException implements UserFixableException {

    public MalReconnectRequiredException() {
        super("The MyAnimeList link has expired.");
    }

    @Override
    public String advice() {
        return "The MyAnimeList link has expired. Reconnect MyAnimeList in settings, then try again.";
    }
}
