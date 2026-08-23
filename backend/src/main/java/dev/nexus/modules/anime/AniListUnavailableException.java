package dev.nexus.modules.anime;

/** AniList could not be reached or answered with something unusable. */
public class AniListUnavailableException extends RuntimeException {

    public AniListUnavailableException(String message) {
        super(message);
    }

    public AniListUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
