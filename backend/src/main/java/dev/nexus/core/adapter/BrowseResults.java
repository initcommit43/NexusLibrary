package dev.nexus.core.adapter;

import java.util.List;

/**
 * One page of a browse shelf.
 *
 * <p>{@code hasMore} rather than a total: AniList will report a count, IGDB will not, and a
 * "next" button only ever needs to know whether there is a next. Asking every source for a
 * number half of them have to guess at buys nothing.
 */
public record BrowseResults(List<ItemSearchResult> items, boolean hasMore) {

    public static BrowseResults empty() {
        return new BrowseResults(List.of(), false);
    }
}
