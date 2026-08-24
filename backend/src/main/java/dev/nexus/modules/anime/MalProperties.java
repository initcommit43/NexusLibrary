package dev.nexus.modules.anime;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MAL connects the way AniList does — OAuth, so a private list reads like a public one —
 * but its flow is not AniList's: PKCE is mandatory (and only the {@code plain} method),
 * the token endpoint takes form-encoded bodies, and tokens live about a month, so the
 * refresh credentials matter here in a way AniList's year-long tokens never made them.
 *
 * <p>Like AniList's, deliberately not required at startup: missing credentials should
 * disable connecting MAL, not stop the app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.mal")
public record MalProperties(
        String apiUrl,
        String clientId,
        /** Blank for a client registered as public; PKCE carries the proof instead. */
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,
        /** MAL documents no hard limit; this stays polite the way the Steam pacing does. */
        @Positive double requestsPerSecond,

        /** How long to wait before retrying a gateway error, growing per attempt. */
        @Positive long retryBackoffMs) {

    /** The secret is not required: a public client proves itself with PKCE alone. */
    public boolean canConnectAccounts() {
        return clientId != null && !clientId.isBlank();
    }
}
