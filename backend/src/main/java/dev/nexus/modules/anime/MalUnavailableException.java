package dev.nexus.modules.anime;

import dev.nexus.core.importing.UpstreamUnavailableException;

/** MyAnimeList could not be reached or answered with something unusable. */
public class MalUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    public MalUnavailableException(String message) {
        super(message);
    }

    public MalUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String serviceName() {
        return "MyAnimeList";
    }
}
