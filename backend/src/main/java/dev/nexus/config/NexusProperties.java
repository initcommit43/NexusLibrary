package dev.nexus.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
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
     */
    public record Security(
            boolean cookieSecure,
            List<String> allowedOrigins,
            String frontendUrl,
            boolean registrationOpen) {}

    public record RateLimit(
            @Positive int authRequestsPerMinute,
            @Positive int searchRequestsPerMinute,
            @Positive int importRequestsPerMinute) {}
}
