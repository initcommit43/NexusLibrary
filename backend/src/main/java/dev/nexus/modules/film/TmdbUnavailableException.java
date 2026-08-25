package dev.nexus.modules.film;

import dev.nexus.core.importing.UpstreamUnavailableException;
import java.util.Optional;

/** TMDB could not be reached or answered with something unusable. */
public class TmdbUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    /** TMDB's own words from {@code status_message}, when it produced any; null otherwise. */
    private final String upstreamMessage;

    public TmdbUnavailableException(String message) {
        super(message);
        this.upstreamMessage = null;
    }

    public TmdbUnavailableException(String message, String upstreamMessage) {
        super(message);
        this.upstreamMessage = upstreamMessage;
    }

    public TmdbUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.upstreamMessage = null;
    }

    /**
     * TMDB answers errors with a readable {@code status_message} — "Invalid API key", "Your
     * request count is over the allowed limit" — which says more than a status code can.
     */
    @Override
    public Optional<String> serviceSays() {
        return Optional.ofNullable(upstreamMessage);
    }

    @Override
    public String serviceName() {
        return "TMDB";
    }
}
