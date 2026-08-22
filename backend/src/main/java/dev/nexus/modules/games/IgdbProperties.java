package dev.nexus.modules.games;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexus.igdb")
/**
 * Credentials are deliberately not required at startup. The games module is one module of
 * several, so missing IGDB keys should disable game search, not stop the whole app booting.
 */
public record IgdbProperties(
        String clientId,
        String clientSecret,
        String apiBaseUrl,
        String tokenUrl,
        /** IGDB permits 4 requests per second. */
        @Positive int requestsPerSecond) {}
