package dev.nexus.core.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * Collects where a request spent its time, and reports it as {@code Server-Timing}.
 *
 * <p>Browsers render that header in the network panel beside their own measurements, so a
 * slow page can be read as "the server waited on AniList" or "the server was fast and the
 * images are heavy" without adding a profiler or guessing from the outside.
 *
 * <p>Request-scoped: each request collects its own marks, and a proxy hands singletons the
 * right one.
 */
@Component
@RequestScope
public class ServerTimings {

    private final Map<String, Long> marks = new LinkedHashMap<>();

    /** Times one step and hands its result straight back, so callers read as they did before. */
    public <T> T time(String name, Supplier<T> work) {
        long startedAt = System.nanoTime();
        try {
            return work.get();
        } finally {
            record(name, (System.nanoTime() - startedAt) / 1_000_000);
        }
    }

    /** Repeated marks under one name add up: three AniList calls report as one total. */
    public void record(String name, long millis) {
        marks.merge(name, millis, Long::sum);
    }

    public Optional<String> header() {
        if (marks.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(marks.entrySet().stream()
                .map(mark -> "%s;dur=%d".formatted(mark.getKey(), mark.getValue()))
                .collect(Collectors.joining(", ")));
    }
}
