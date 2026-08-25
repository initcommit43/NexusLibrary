package dev.nexus.modules.film;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Trakt needs credentials for everything, unlike TMDB: even a public profile is read with
 * the application's api key, and a user's own history needs their token on top.
 *
 * <p>Not required at startup, like every other provider's: missing Trakt credentials should
 * disable connecting an account, not stop the app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.trakt")
public record TraktProperties(
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,

        /** Trakt documents 1000 calls every 5 minutes; an import spends a handful. */
        @Positive int requestsPerSecond) {

    public boolean canConnectAccounts() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }
}
