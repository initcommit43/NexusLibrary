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
public record NexusProperties(Jwt jwt, Security security, RateLimit rateLimit, Registration registration) {

    public record Jwt(
            // HS256 needs >= 256 bits of key material; a short secret weakens every token.
            @NotBlank @Size(min = 32) String secret,
            @Positive long accessTtlMinutes,
            @Positive long refreshTtlDays,
            /**
             * How long after a refresh token is retired its holder may present it again
             * without that being read as theft. Covers two clients refreshing at once; see
             * {@link dev.nexus.auth.RefreshTokenService#renew}.
             */
            @PositiveOrZero long refreshReuseGraceSeconds) {}

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

    /**
     * What an address has to be before it may open an account.
     *
     * <p>Both checks are switchable because both can be wrong about a real person. A blocklist
     * ages: a domain someone actually uses can end up on one. A DNS lookup depends on the
     * network this process happens to be on. Neither is worth a stranger being unable to sign
     * up with no way for anyone to see why, so each can be turned off without a rebuild.
     *
     * @param blockDisposableDomains refuse the throwaway-inbox services listed in
     *     {@code disposable-email-domains.txt}.
     * @param requireDeliverableDomain refuse a domain that publishes no way to receive mail.
     *     This is what separates a typo or an invented address from a real one; it costs a DNS
     *     lookup per sign-up and nothing afterwards.
     * @param deliverabilityTimeoutMillis how long that lookup may take before the address is
     *     allowed through unchecked. A resolver having a bad day must not become a closed door.
     */
    public record Registration(
            boolean blockDisposableDomains,
            boolean requireDeliverableDomain,
            @Positive int deliverabilityTimeoutMillis) {}
}
