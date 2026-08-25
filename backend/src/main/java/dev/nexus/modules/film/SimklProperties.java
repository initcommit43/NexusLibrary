package dev.nexus.modules.film;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Simkl identifies the calling application on every request, not just at authorization: the
 * client id, an app name and an app version ride along as query parameters, and the user
 * agent has to name the same app.
 *
 * <p>Not required at startup, like every other provider's: missing Simkl credentials should
 * disable connecting an account, not stop the app booting.
 */
@Validated
@ConfigurationProperties(prefix = "nexus.simkl")
public record SimklProperties(
        String apiBaseUrl,
        String clientId,
        String clientSecret,
        String authorizeUrl,
        String tokenUrl,

        /** Sent as {@code app-name} and in the user agent; Simkl wants it lowercase. */
        String appName,
        String appVersion,

        @Positive int requestsPerSecond) {

    public boolean canConnectAccounts() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** The identification every call carries, appended to whatever query a path already has. */
    public String identifyingParams() {
        return "client_id=%s&app-name=%s&app-version=%s".formatted(clientId, appName, appVersion);
    }

    public String userAgent() {
        return appName + "/" + appVersion;
    }
}
