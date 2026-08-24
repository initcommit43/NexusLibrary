package dev.nexus.modules.anime;

import dev.nexus.core.importing.UpstreamUnavailableException;
import java.util.Optional;

/**
 * AniList could not be reached or answered with something unusable.
 *
 * <p>Carries whether the failure is worth another attempt. AniList sits behind Cloudflare
 * and answers 502 or 504 intermittently under no load at all, and rate limiting is a wait
 * rather than a refusal — but a malformed query fails identically every time, and retrying
 * it only spends the budget three times over.
 */
public class AniListUnavailableException extends RuntimeException implements UpstreamUnavailableException {

    private final boolean worthRetrying;
    /** AniList's own words from the error body, when it produced any; null otherwise. */
    private final String upstreamMessage;

    public AniListUnavailableException(String message) {
        this(message, false);
    }

    public AniListUnavailableException(String message, boolean worthRetrying) {
        super(message);
        this.worthRetrying = worthRetrying;
        this.upstreamMessage = null;
    }

    public AniListUnavailableException(String message, int status, String upstreamMessage) {
        super(message);
        // 429 is "wait"; 5xx is the gateway, not the query. Everything else is our fault.
        this.worthRetrying = status == 429 || status >= 500;
        this.upstreamMessage = upstreamMessage;
    }

    public AniListUnavailableException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public AniListUnavailableException(String message, Throwable cause, boolean worthRetrying) {
        super(message, cause);
        this.worthRetrying = worthRetrying;
        this.upstreamMessage = null;
    }

    public boolean isWorthRetrying() {
        return worthRetrying;
    }

    @Override
    public String serviceName() {
        return "AniList";
    }

    @Override
    public Optional<String> serviceSays() {
        return Optional.ofNullable(upstreamMessage);
    }
}
