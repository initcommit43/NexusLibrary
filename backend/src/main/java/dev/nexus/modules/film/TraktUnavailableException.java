package dev.nexus.modules.film;

import dev.nexus.core.importing.UpstreamUnavailableException;
import java.util.Optional;

/** Trakt could not be reached or answered with something unusable. */
public class TraktUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    /** Trakt's own words from the error body, when it produced any; null otherwise. */
    private final String upstreamMessage;

    public TraktUnavailableException(String message) {
        super(message);
        this.upstreamMessage = null;
    }

    public TraktUnavailableException(String message, String upstreamMessage) {
        super(message);
        this.upstreamMessage = upstreamMessage;
    }

    public TraktUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.upstreamMessage = null;
    }

    @Override
    public Optional<String> serviceSays() {
        return Optional.ofNullable(upstreamMessage);
    }

    @Override
    public String serviceName() {
        return "Trakt";
    }
}
