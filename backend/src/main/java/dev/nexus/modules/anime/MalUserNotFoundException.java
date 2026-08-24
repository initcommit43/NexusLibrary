package dev.nexus.modules.anime;

import dev.nexus.core.importing.UserFixableException;

/** MAL has no user by that name — almost always a typo in the username. */
public class MalUserNotFoundException extends RuntimeException implements UserFixableException {

    private final String username;

    public MalUserNotFoundException(String username) {
        super("MyAnimeList has no user named " + username);
        this.username = username;
    }

    @Override
    public String advice() {
        return "MyAnimeList has no user named \"" + username
                + "\". Check the spelling — it is the name in your profile URL.";
    }
}
