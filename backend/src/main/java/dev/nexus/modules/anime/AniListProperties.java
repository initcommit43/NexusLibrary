package dev.nexus.modules.anime;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AniList needs no credentials to read: search and media lookups are public. Only the
 * library import, which reads a user's own list, involves OAuth.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.anilist")
public record AniListProperties(
        String apiUrl,
        /** AniList permits 90 requests per minute, and answers 429 with Retry-After past it. */
        @Positive int requestsPerMinute) {}
