package dev.nexus.modules.anime;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Reading AniList needs no credentials: search and media lookups are public. The client id
 * and secret are only for the import, which reads a user's own list on their behalf.
 *
 * <p>Deliberately not required at startup, like IGDB's: missing AniList credentials should
 * disable connecting an account, not stop the whole app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.anilist")
public record AniListProperties(
        String apiUrl,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,
        /** AniList permits 90 requests per minute, and answers 429 with Retry-After past it. */
        @Positive int requestsPerMinute,

        /** How long to wait before retrying a gateway error, doubling per attempt. */
        @Positive long retryBackoffMs) {

    public boolean canConnectAccounts() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
