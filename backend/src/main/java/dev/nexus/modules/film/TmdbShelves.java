package dev.nexus.modules.film;

import dev.nexus.core.adapter.BrowseShelf;
import java.util.List;
import java.util.Set;

/**
 * What the film and TV browse pages offer, and the TMDB list behind each row.
 *
 * <p>The two kinds get near-identical shelves with different words, because TMDB models them
 * that way: a film is "in cinemas" and a show is "on the air", and the endpoints are named
 * accordingly. Unlike AniList's anime and manga — which genuinely differ in what can be asked
 * of them — the difference here is vocabulary, so the rows line up one for one.
 */
final class TmdbShelves {

    /** One shelf: what a reader sees, and where TMDB keeps it. */
    record Definition(String id, String label, String path, boolean trending) {

        static Definition list(String id, String label, String path) {
            return new Definition(id, label, path, false);
        }

        /** Trending is its own endpoint rather than a list under the kind. */
        static Definition trending(String id, String label, String window) {
            return new Definition(id, label, window, true);
        }
    }

    private static final List<Definition> MOVIES = List.of(
            Definition.trending("trending", "Trending this week", "week"),
            Definition.list("popular", "Popular now", "popular"),
            Definition.list("in-cinemas", "In cinemas now", "now_playing"),
            Definition.list("coming-soon", "Coming soon", "upcoming"),
            Definition.list("top", "Top rated films", "top_rated"));

    private static final List<Definition> SHOWS = List.of(
            Definition.trending("trending", "Trending this week", "week"),
            Definition.list("popular", "Popular now", "popular"),
            Definition.list("on-the-air", "On the air", "on_the_air"),
            Definition.list("airing-today", "Airing today", "airing_today"),
            Definition.list("top", "Top rated shows", "top_rated"));

    private TmdbShelves() {}

    static List<Definition> definitionsFor(TmdbKind kind) {
        return kind == TmdbKind.SHOW ? SHOWS : MOVIES;
    }

    /** The week's own list first, then the standing one. */
    private static final Set<String> ON_HOME = Set.of("trending", "popular");

    static List<BrowseShelf> shelvesFor(TmdbKind kind) {
        return definitionsFor(kind).stream()
                .map(definition ->
                        new BrowseShelf(definition.id(), definition.label(), ON_HOME.contains(definition.id())))
                .toList();
    }

    /** Null for an id no shelf claims, which is a bug rather than something to query for. */
    static Definition find(TmdbKind kind, String shelfId) {
        return definitionsFor(kind).stream()
                .filter(definition -> definition.id().equals(shelfId))
                .findFirst()
                .orElse(null);
    }
}
