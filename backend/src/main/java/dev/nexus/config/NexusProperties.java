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

    public record Security(boolean cookieSecure, List<String> allowedOrigins) {}

    public record RateLimit(@Positive int authRequestsPerMinute, @Positive int searchRequestsPerMinute) {}
}
