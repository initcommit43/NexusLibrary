package dev.nexus.core.cache;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** How long a cached item stays fresh, and how hard a refresh is allowed to try. */
@Validated
@ConfigurationProperties(prefix = "nexus.refresh")
public record RefreshProperties(
        @NotNull Duration ongoingTtl,
        @NotNull Duration upcomingTtl,

        /**
         * How long an attempted refresh suppresses the next one for the same item. Also the
         * backoff for a source that is down: a failed attempt still counts as an attempt, so
         * an outage costs one call per item per window rather than one per read.
         */
        @NotNull Duration retryAfter,

        /**
         * Caps how many items a single read may put in flight. A first look at a large
         * library would otherwise queue a refresh for every stale title at once; the
         * remainder are simply picked up by the reads that follow.
         */
        @Positive int maxItemsPerRead) {}
