package dev.nexus.core.adapter;

import java.util.List;

/**
 * One control on a browse page's filter bar, described by the adapter that can answer it.
 *
 * <p>Declared rather than hardcoded in the frontend for the same reason browse shelves are:
 * a season is AniList's idea and a platform is IGDB's, and a module that has neither should
 * show neither. The client renders whatever list it is handed and sends the values back.
 *
 * @param id the key this filter's value travels under, and part of a URL
 * @param label what a reader sees above the control
 * @param kind how to render it
 * @param options the values it accepts, empty for {@link Kind#TEXT}
 * @param defaultValue for {@link Kind#SORT}, the order used when none is chosen; null otherwise
 */
public record FilterField(String id, String label, Kind kind, List<FilterOption> options, String defaultValue) {

    public enum Kind {
        /** A free-text box. */
        TEXT,
        /** One of the options, or none. */
        SELECT,
        /** Any number of the options. */
        MULTI,
        /** The order the results come in, drawn beside the results rather than on the bar. */
        SORT
    }

    public FilterField {
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static FilterField text(String id, String label) {
        return new FilterField(id, label, Kind.TEXT, List.of(), null);
    }

    public static FilterField select(String id, String label, List<FilterOption> options) {
        return new FilterField(id, label, Kind.SELECT, options, null);
    }

    public static FilterField multi(String id, String label, List<FilterOption> options) {
        return new FilterField(id, label, Kind.MULTI, options, null);
    }

    /**
     * The orders a source can sort by, in the order its menu lists them, under the id
     * {@code sort}, and the one it uses when none is chosen.
     */
    public static FilterField sort(List<FilterOption> options, String defaultValue) {
        return new FilterField(SORT_ID, "Sort", Kind.SORT, options, defaultValue);
    }

    /** Where the chosen order travels, the same for every source. */
    public static final String SORT_ID = "sort";

    /**
     * One choosable value. The label is carried rather than derived, because the wire value is
     * the source's own — {@code NOT_YET_RELEASED} is what AniList wants and not what to show.
     */
    public record FilterOption(String value, String label) {}
}
