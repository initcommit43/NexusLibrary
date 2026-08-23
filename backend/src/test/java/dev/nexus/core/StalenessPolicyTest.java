package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.cache.RefreshProperties;
import dev.nexus.core.cache.StalenessPolicy;
import dev.nexus.core.domain.ItemState;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class StalenessPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-23T12:00:00Z");
    private static final Duration TTL = Duration.ofHours(24);

    private final StalenessPolicy policy =
            new StalenessPolicy(new RefreshProperties(TTL, TTL, Duration.ofMinutes(15), 50));

    @Test
    void aReleasedItemIsNeverStaleHoweverLongItHasBeenCached() {
        assertThat(policy.isStale(ItemState.RELEASED, NOW.minus(Duration.ofDays(3650)), NOW))
                .isFalse();
    }

    @Test
    void anOngoingItemIsStaleOnceItsTtlHasPassed() {
        assertThat(policy.isStale(ItemState.ONGOING, NOW.minus(TTL).minusSeconds(1), NOW))
                .isTrue();
    }

    @Test
    void anOngoingItemInsideItsTtlIsStillFresh() {
        assertThat(policy.isStale(ItemState.ONGOING, NOW.minus(Duration.ofHours(23)), NOW))
                .isFalse();
    }

    @Test
    void anUpcomingItemIsStaleOnceItsTtlHasPassed() {
        assertThat(policy.isStale(ItemState.UPCOMING, NOW.minus(Duration.ofDays(2)), NOW))
                .isTrue();
    }

    @Test
    void anItemWithNoTimestampReadBackYetCountsAsFresh() {
        assertThat(policy.isStale(ItemState.ONGOING, null, NOW)).isFalse();
    }
}
