package dev.nexus.modules.anime;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MAL's v2 API reads a public list with nothing but an application client id in a header —
 * no per-user OAuth, because this import never writes back and never needs a private list.
 * The same trade as Steam: one registered credential, and the user contributes a username.
 *
 * <p>Like AniList's, deliberately not required at startup: a missing client id should
 * disable connecting MAL, not stop the app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.mal")
public record MalProperties(
        String apiUrl,
        String clientId,
        /** MAL documents no hard limit; this stays polite the way the Steam pacing does. */
        @Positive double requestsPerSecond,

        /** How long to wait before retrying a gateway error, growing per attempt. */
        @Positive long retryBackoffMs) {

    public boolean canConnectAccounts() {
        return clientId != null && !clientId.isBlank();
    }
}
