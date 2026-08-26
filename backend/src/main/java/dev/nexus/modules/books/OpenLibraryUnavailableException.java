package dev.nexus.modules.books;

import dev.nexus.core.importing.UpstreamUnavailableException;
import java.util.Optional;

/** Open Library could not be reached or answered with something unusable. */
public class OpenLibraryUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    /** Open Library's own words, when it produced any; null otherwise. */
    private final String upstreamMessage;

    public OpenLibraryUnavailableException(String message) {
        super(message);
        this.upstreamMessage = null;
    }

    public OpenLibraryUnavailableException(String message, String upstreamMessage) {
        super(message);
        this.upstreamMessage = upstreamMessage;
    }

    public OpenLibraryUnavailableException(String message, Throwable cause) {
        super(message, cause);
        this.upstreamMessage = null;
    }

    @Override
    public Optional<String> serviceSays() {
        return Optional.ofNullable(upstreamMessage);
    }

    @Override
    public String serviceName() {
        return "Open Library";
    }
}
