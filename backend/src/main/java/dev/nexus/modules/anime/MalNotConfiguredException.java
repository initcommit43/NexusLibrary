package dev.nexus.modules.anime;

/** MAL has no client id configured, so nothing can be asked of it. */
public class MalNotConfiguredException extends RuntimeException {

    public MalNotConfiguredException() {
        super("MyAnimeList is not configured on this server.");
    }
}
