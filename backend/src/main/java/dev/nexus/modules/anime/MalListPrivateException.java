package dev.nexus.modules.anime;

import dev.nexus.core.importing.UserFixableException;

/**
 * MAL refused the list. Without per-user OAuth there is no reading a private list, so the
 * fix is the visibility setting — a thing only the user can change, and can.
 */
public class MalListPrivateException extends RuntimeException implements UserFixableException {

    public MalListPrivateException() {
        super("MyAnimeList refused access to this list.");
    }

    @Override
    public String advice() {
        return "MyAnimeList would not share this list. Set your anime and manga lists to "
                + "Public in MAL's privacy settings, then try again.";
    }
}
