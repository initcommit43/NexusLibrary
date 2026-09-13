package dev.nexus.core;

import static org.assertj.core.api.Assertions.assertThat;

import dev.nexus.core.adapter.HeldOptions;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class HeldOptionsTest {

    private static final List<String> FALLBACK = List.of("fallback");

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-13T12:00:00Z"));
    private final HeldOptions<String> options = new HeldOptions<>(now::get);
    private final AtomicInteger asked = new AtomicInteger();

    private Supplier<List<String>> answering(List<String> answer) {
        return () -> {
            asked.incrementAndGet();
            return answer;
        };
    }

    private Supplier<List<String>> failing() {
        return () -> {
            asked.incrementAndGet();
            throw new IllegalStateException("source down");
        };
    }

    @Test
    void keepsTheFirstGoodAnswerForTheRun() {
        assertThat(options.get(answering(List.of("Action")), FALLBACK, e -> {})).containsExactly("Action");

        now.set(now.get().plus(HeldOptions.RETRY_AFTER.multipliedBy(100)));
        assertThat(options.get(answering(List.of("Other")), FALLBACK, e -> {})).containsExactly("Action");
        assertThat(asked).hasValue(1);
    }

    @Test
    void aFailureIsNotAskedAgainUntilTheWaitIsOver() {
        AtomicInteger reported = new AtomicInteger();

        assertThat(options.get(failing(), FALLBACK, e -> reported.incrementAndGet())).isEqualTo(FALLBACK);
        now.set(now.get().plus(HeldOptions.RETRY_AFTER.minusSeconds(1)));
        assertThat(options.get(answering(List.of("Action")), FALLBACK, e -> {})).isEqualTo(FALLBACK);

        assertThat(asked).hasValue(1);
        assertThat(reported).hasValue(1);

        now.set(now.get().plusSeconds(1));
        assertThat(options.get(answering(List.of("Action")), FALLBACK, e -> {})).containsExactly("Action");
        assertThat(asked).hasValue(2);
    }

    @Test
    void anEmptyAnswerWaitsLikeAFailure() {
        assertThat(options.get(answering(List.of()), FALLBACK, e -> {})).isEqualTo(FALLBACK);
        assertThat(options.get(answering(List.of("Action")), FALLBACK, e -> {})).isEqualTo(FALLBACK);

        assertThat(asked).hasValue(1);
    }
}
