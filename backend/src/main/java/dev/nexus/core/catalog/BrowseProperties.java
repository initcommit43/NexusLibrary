package dev.nexus.core.catalog;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** How long a browse shelf is served from memory before it is fetched again. */
@Validated
@ConfigurationProperties(prefix = "nexus.browse")
public record BrowseProperties(@NotNull Duration ttl) {}
