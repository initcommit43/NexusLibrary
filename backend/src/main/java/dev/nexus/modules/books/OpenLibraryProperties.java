package dev.nexus.modules.books;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Open Library needs no credential at all — no key, no OAuth, no registration. It is the only
 * source in this app with nothing to configure and nothing to keep secret, which is also why
 * book search is the one thing here that cannot be switched off by a missing environment
 * variable.
 *
 * <p>What it asks for instead is a User-Agent that identifies the caller and gives them
 * someone to contact. It is run by the Internet Archive on donated infrastructure, and a
 * caller who hides behind a default client library User-Agent gets throttled, reasonably.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.open-library")
public record OpenLibraryProperties(
        String apiBaseUrl,

        /** Where cover images are served from; a different host to the API's. */
        String coverBaseUrl,

        /** One of Open Library's three cover sizes: S, M or L. */
        String coverSize,

        /** Sent on every request, per Open Library's stated conditions of use. */
        String userAgent,

        /**
         * No published ceiling, because there is no account to meter. This is a courtesy to a
         * nonprofit rather than a limit anyone enforces — an import of a large library should
         * not look like an attack.
         */
        @Positive int requestsPerSecond) {

    /**
     * Always true. Present so the catalogue behaves uniformly with the sources that can be
     * unconfigured, rather than making book search a special case at every call site.
     */
    public boolean canSearch() {
        return true;
    }

    /** Open Library serves a placeholder for an unknown id, so a null cover has to stay null. */
    public String coverUrl(Object coverId) {
        return coverId == null ? null : coverBaseUrl + "/b/id/" + coverId + "-" + coverSize + ".jpg";
    }

    /**
     * An author's portrait, from the same host as the covers: {@code /a/} rather than
     * {@code /b/}, and keyed by their Open Library id rather than by a photo id.
     */
    public String authorPhotoUrl(String authorId) {
        return authorId == null || authorId.isBlank()
                ? null
                : coverBaseUrl + "/a/olid/" + authorId + "-" + coverSize + ".jpg";
    }
}
