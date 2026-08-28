package dev.nexus.core.adapter;

import java.util.List;
import java.util.Map;

/**
 * What a reader narrowed a browse page down to, keyed by the field ids the adapter published.
 *
 * <p>Deliberately not a record of named fields. A season is AniList's idea, a platform is
 * IGDB's, and core has no business holding either — it carries whatever the adapter said it
 * could answer, straight back to the adapter that said it. A module gains a filter without
 * this class changing.
 */
public record DiscoverFilters(Map<String, List<String>> values) {

    public DiscoverFilters {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public static DiscoverFilters none() {
        return new DiscoverFilters(Map.of());
    }

    /** Every value chosen for a field, in the order they arrived. */
    public List<String> all(String field) {
        return values.getOrDefault(field, List.of()).stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    /** The single value of a field that takes one, or null where it was left alone. */
    public String one(String field) {
        return all(field).stream().findFirst().orElse(null);
    }

    /** The value of a field that takes a number, or null where it was left alone or is not one. */
    public Integer number(String field) {
        String value = one(field);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            // A field that declared years and was handed a word narrows to nothing, not to an error.
            return null;
        }
    }

    /**
     * Whether this narrows anything at all. An unfiltered browse page is the shelves, not a
     * grid of everything the source holds, so core needs to tell the two apart.
     */
    public boolean isEmpty() {
        return values.keySet().stream().allMatch(field -> all(field).isEmpty());
    }
}
