package dev.nexus.core.adapter;

/**
 * One row on a module's browse page — "Popular now", "Coming soon".
 *
 * <p>The label travels with the id rather than living in the frontend's registry, because a
 * shelf is the adapter's idea: only IGDB knows that games sort meaningfully by rating count,
 * and only AniList knows what a season is. Adding a shelf is then a change to one adapter,
 * the way adding a module is a change to one bean.
 *
 * @param id what {@link MetadataAdapter#browse} is called back with; stable, and part of a URL
 * @param label what a reader sees above the row
 * @param onHome whether the module wants this row on its home page. The module decides,
 *     because only it knows which of its rows are the ones worth leading with — a games home
 *     is about what is next, an anime home about what is airing.
 * @param onBrowse whether the row belongs on browse. Separate from {@code onHome} rather than
 *     implied by it: a row the home page leads with is not automatically a category anyone
 *     wants to browse, and while home was a filtered view of browse there was no way to say
 *     so — asking for a row on the home page put it on browse too, whether or not that was
 *     wanted.
 */
public record BrowseShelf(String id, String label, boolean onHome, boolean onBrowse) {

    /** A row of the browse page and nowhere else, which is what most of them are. */
    public BrowseShelf(String id, String label) {
        this(id, label, false, true);
    }

    /** A browse row the home page also leads with. */
    public BrowseShelf(String id, String label, boolean onHome) {
        this(id, label, onHome, true);
    }
}
