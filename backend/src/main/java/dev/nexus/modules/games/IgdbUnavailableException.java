package dev.nexus.modules.games;

import dev.nexus.core.importing.UpstreamUnavailableException;

/** IGDB could not be reached or answered with something unusable. */
public class IgdbUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    public IgdbUnavailableException(String message) {
        super(message);
    }

    public IgdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Nobody outside this codebase has heard of IGDB; what it is to a reader is the catalogue. */
    @Override
    public String serviceName() {
        return "The game database";
    }
}
