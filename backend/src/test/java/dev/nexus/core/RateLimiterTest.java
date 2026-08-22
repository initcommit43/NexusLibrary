package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.nexus.core.web.RateLimitExceededException;
import dev.nexus.core.web.RateLimiter;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    @Test
    void allowsRequestsUpToTheLimit() {
        RateLimiter limiter = new RateLimiter();

        assertThatCode(() -> {
                    for (int i = 0; i < 3; i++) {
                        limiter.check("client-a", 3);
                    }
                })
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsTheRequestPastTheLimit() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.check("client-a", 3);
        }

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(() -> limiter.check("client-a", 3));
    }

    @Test
    void countsEachKeySeparately() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 3; i++) {
            limiter.check("client-a", 3);
        }

        assertThatCode(() -> limiter.check("client-b", 3)).doesNotThrowAnyException();
    }
}
