package dev.nexus.modules.games;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexus.igdb")
public record IgdbProperties(
        @NotBlank String clientId,
        @NotBlank String clientSecret,
        String apiBaseUrl,
        String tokenUrl,
        /** IGDB permits 4 requests per second. */
        @Positive int requestsPerSecond) {}
