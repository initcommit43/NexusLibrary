package dev.nexus.modules.books;

import dev.nexus.core.adapter.BrowseShelf;
import java.util.List;
import java.util.Set;

/**
 * What the books browse page offers, and where Open Library keeps each row.
 *
 * <p>Books are the awkward module here. Open Library publishes no popularity ranking and no
 * editorial lists — it is a catalogue, not a storefront — so there is no equivalent of IGDB's
 * rating count or TMDB's "popular". What it does publish is a trending list, drawn from what
 * people are actually adding to their reading logs, and subject indexes.
 *
 * <p>So the shelves are two of a kind: what people are reading now, at three windows, and
 * then a few subjects worth opening a books page on. A subject row is not personalised or
 * ranked by quality, which is why they sit below the trending ones rather than above.
 */
final class OpenLibraryShelves {

    /** One shelf: what a reader sees, and which Open Library list produces it. */
    record Definition(String id, String label, String window, String subject) {

        static Definition trending(String id, String label, String window) {
            return new Definition(id, label, window, null);
        }

        static Definition subject(String id, String label, String subject) {
            return new Definition(id, label, null, subject);
        }

        boolean isTrending() {
            return window != null;
        }
    }

    /**
     * One trending row, not several. Open Library publishes daily, weekly, monthly and yearly
     * windows, but they return the same handful of titles in a slightly different order — the
     * top four are identical across all four windows — so three trending shelves would read as
     * the same shelf printed three times. The subjects carry the rest of the page instead.
     */
    private static final List<Definition> SHELVES = List.of(
            Definition.trending("trending", "Trending now", "weekly"),
            Definition.subject("fiction", "Fiction", "fiction"),
            Definition.subject("fantasy", "Fantasy", "fantasy"),
            Definition.subject("science-fiction", "Science fiction", "science_fiction"),
            Definition.subject("mystery", "Mystery and detective", "detective_and_mystery_stories"),
            Definition.subject("horror", "Horror", "horror"),
            Definition.subject("romance", "Romance", "romance"));

    /** What is being read now, and the broadest shelf under it. */
    private static final Set<String> ON_HOME = Set.of("trending", "fiction");

    private OpenLibraryShelves() {}

    static List<BrowseShelf> shelves() {
        return SHELVES.stream()
                .map(definition ->
                        new BrowseShelf(definition.id(), definition.label(), ON_HOME.contains(definition.id())))
                .toList();
    }

    /** Null for an id no shelf claims, which is a bug rather than something to query for. */
    static Definition find(String shelfId) {
        return SHELVES.stream()
                .filter(definition -> definition.id().equals(shelfId))
                .findFirst()
                .orElse(null);
    }
}
