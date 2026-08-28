package dev.nexus.core.adapter;

import java.util.List;

/**
 * What a reader narrowed a browse page down to.
 *
 * <p>Every field is optional and a null one means "not filtered" rather than "matches null" —
 * the adapter is what decides how to say that to its own API. Core carries the answer without
 * knowing what a season is, which is what keeps a games module from having to have one.
 *
 * @param query free text, matched against titles
 * @param genres all of which must apply, not any
 * @param year the year the title belongs to, as the source counts it
 * @param season a value from the {@code season} filter this media type declared
 * @param format a value from the {@code format} filter this media type declared
 * @param status a value from the {@code status} filter this media type declared
 */
public record DiscoverFilters(
        String query, List<String> genres, Integer year, String season, String format, String status) {

    public DiscoverFilters {
        genres = genres == null ? List.of() : List.copyOf(genres);
    }

    public static DiscoverFilters none() {
        return new DiscoverFilters(null, List.of(), null, null, null, null);
    }

    /**
     * Whether this narrows anything at all. An unfiltered browse page is the shelves, not a
     * grid of everything the source holds, so core needs to tell the two apart.
     */
    public boolean isEmpty() {
        return blank(query) && genres.isEmpty() && year == null && blank(season) && blank(format) && blank(status);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
