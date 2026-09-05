package dev.nexus.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "nexus")
public record NexusProperties(Jwt jwt, Security security, RateLimit rateLimit) {

    public record Jwt(
            // HS256 needs >= 256 bits of key material; a short secret weakens every token.
            @NotBlank @Size(min = 32) String secret,
            @Positive long accessTtlMinutes,
            @Positive long refreshTtlDays) {}

    /**
     * @param registrationOpen whether anyone may still create an account. A deployment with a
     *     public URL is reachable by whoever finds it, and an open sign-up there is a stranger
     *     spending someone else's API budget. Existing accounts sign in either way.
     * @param trustedProxyCount how many proxies sit in front, which is how far in from the end
     *     of {@code X-Forwarded-For} the caller's own address is. 0 locally, 1 behind Railway,
     *     2 with a CDN in front of that. Set it too high and a forged entry gets read as the
     *     caller; see {@link dev.nexus.core.web.ClientIpResolver}.
     * @param passwordResetTtlMinutes how long a mailed reset link works. Short on purpose: it
     *     sets a password without the old one being known, and it lives in an inbox.
     */
    public record Security(
            boolean cookieSecure,
            List<String> allowedOrigins,
            String frontendUrl,
            boolean registrationOpen,
            @PositiveOrZero int trustedProxyCount,
            @Positive long passwordResetTtlMinutes) {}

    public record RateLimit(
            @Positive int authRequestsPerMinute,
            @Positive int searchRequestsPerMinute,
            @Positive int importRequestsPerMinute) {}
}
