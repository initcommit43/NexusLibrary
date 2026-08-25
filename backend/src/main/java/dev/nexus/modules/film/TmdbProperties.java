package dev.nexus.modules.film;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * TMDB reads need one credential and no OAuth: the v4 read access token is application-wide
 * and never expires, so there is nothing to refresh and no per-user consent to obtain.
 *
 * <p>Not required at startup, like IGDB's and AniList's: a missing token should disable film
 * and TV search, not stop the whole app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.tmdb")
public record TmdbProperties(
        String apiBaseUrl,

        /**
         * Where posters are served from. TMDB publishes this through its {@code
         * /configuration} endpoint, but the host has not changed in a decade and fetching it
         * would cost a request before every cold search.
         */
        String imageBaseUrl,

        /** One of TMDB's fixed poster widths. w500 is what a cover grid needs. */
        String posterSize,

        /** The v4 "API Read Access Token", sent as a bearer — not the v3 key. */
        String accessToken,

        /** TMDB no longer publishes a hard ceiling; this keeps a big import from testing it. */
        @Positive int requestsPerSecond) {

    public boolean canSearch() {
        return accessToken != null && !accessToken.isBlank();
    }

    public String posterUrl(String posterPath) {
        return posterPath == null || posterPath.isBlank() ? null : imageBaseUrl + posterSize + posterPath;
    }
}
