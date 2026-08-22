package dev.nexus.modules.games;

/** IGDB could not be reached or answered with something unusable. */
public class IgdbUnavailableException extends RuntimeException {

    public IgdbUnavailableException(String message) {
        super(message);
    }

    public IgdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
