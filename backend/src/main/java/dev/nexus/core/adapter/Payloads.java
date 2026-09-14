package dev.nexus.core.adapter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reading the loosely typed maps a catalogue answers with.
 *
 * <p>Every source arrives as {@code Map<String, Object>} parsed from JSON, so every adapter
 * needs the same four questions answered: what is this value as text, what are the names in
 * this list of objects, which is the first usable url, and does this belong in the metadata at
 * all. Written once here rather than once per adapter — four copies of "is it blank" is four
 * chances for one of them to disagree about what an empty summary means.
 */
public final class Payloads {

    private Payloads() {}

    /** A value as text, keeping null as null rather than as the word. */
    public static String string(Object value) {
        return value == null ? null : value.toString();
    }

    /** The {@code name} of every object in a list of them — how every source spells a genre. */
    public static List<String> names(Object raw) {
        if (!(raw instanceof List<?> entries)) {
            return List.of();
        }
        return entries.stream()
                .filter(Map.class::isInstance)
                .map(entry -> ((Map<?, ?>) entry).get("name"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    /** The first usable url in a list of them, which is where wide-art keys arrive. */
    public static Optional<String> firstUrl(Object raw) {
        if (!(raw instanceof List<?> urls)) {
            return Optional.empty();
        }
        return urls.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(url -> !url.isBlank())
                .findFirst();
    }

    /**
     * Writes a value only where there is one to write.
     *
     * <p>A missing key and a key holding nothing are the same fact to every reader of this
     * metadata, and only one of them costs a row in the cache. Absent, empty and blank all
     * count as nothing.
     */
    public static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        target.put(key, value);
    }
}
