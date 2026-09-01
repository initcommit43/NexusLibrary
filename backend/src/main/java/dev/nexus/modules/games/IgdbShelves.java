package dev.nexus.modules.games;

import dev.nexus.core.adapter.BrowseShelf;
import java.util.List;

/**
 * What the games browse page offers, and where IGDB keeps each row.
 *
 * <p>Games were the module that made the split between home and browse necessary. Every row
 * here used to lead the home page, which left browse showing the home page again under a
 * different heading — four rows, the same four, and no reason to open the second page.
 *
 * <p>So the page is built from three kinds of row. IGDB's popularity tables answer what people
 * are doing right now, which is what a browse page opens on. The date and rating shelves
 * answer what is out and what is worth playing, and are written in the adapter because they
 * need the current time. Genre rows carry the rest of the page, the way subjects carry books:
 * not personalised and not ranked against each other, which is why they sit at the bottom.
 *
 * <p>The four rows the home page leads with are unchanged and in their original order — this
 * page grew around them rather than rearranging them.
 */
final class IgdbShelves {

    /*
     * IGDB's popularity tables, by id from /popularity_types. Theirs rather than the
     * Steam-sourced ones beside them: a shelf of games covers every platform, and a Steam
     * peak-player count only knows about one of them.
     */

    /** How many people opened a game's page, refreshed daily. */
    static final int POPULARITY_VISITS = 1;

    /** How many people have it marked as playing, which is not the same as looking at it. */
    static final int POPULARITY_PLAYING = 3;

    /** How many people have ever marked it played — a standing list, not a moving one. */
    static final int POPULARITY_PLAYED = 4;

    /** Wishlists against unreleased games, which is anticipation rather than attention. */
    static final int POPULARITY_WISHLISTED_UPCOMING = 10;

    /* IGDB genre ids, from /genres. */
    private static final int GENRE_SHOOTER = 5;
    private static final int GENRE_RPG = 12;
    private static final int GENRE_STRATEGY = 15;
    private static final int GENRE_INDIE = 32;

    /*
     * Why these four and not the obvious ones. A genre row earns its place by showing games
     * the rest of the page does not: measured against "top rated", adventure repeats eight of
     * its top ten and platform and puzzle three, because almost everything well reviewed is
     * tagged adventure. Shooters, strategy and indie repeat at most one, and role-playing four
     * of ten, which is the genre being itself rather than the row failing - its own top is
     * Elden Ring where the page's is Super Metroid.
     */

    /**
     * The vote floor under every genre row, and it has to be the high one. A genre sorted by
     * score with a low floor fills with fan projects and stub records carrying a handful of
     * perfect ratings — "Undertale Yellow" over "Elden Ring" under role-playing. At two
     * hundred votes the same query returns the games the genre is actually known for.
     */
    static final int GENRE_VOTE_FLOOR = 200;

    static final String SHELF_POPULAR = "popular";
    static final String SHELF_PLAYING = "playing";
    static final String SHELF_ANTICIPATED = "anticipated";
    static final String SHELF_TOP_RATED = "top-rated";
    static final String SHELF_RECENT = "recent";
    static final String SHELF_COMING_SOON = "coming-soon";
    static final String SHELF_MOST_PLAYED = "most-played";

    /**
     * One shelf: what a reader sees, and which of the three sources answers it.
     *
     * <p>At most one of {@code popularityType} and {@code genreId} is set. Both null means the
     * adapter writes the query itself, which the date and rating rows need because they are
     * relative to now.
     */
    record Definition(String id, String label, boolean onHome, Integer popularityType, Integer genreId) {

        /** A row the adapter builds its own query for. */
        static Definition query(String id, String label, boolean onHome) {
            return new Definition(id, label, onHome, null, null);
        }

        /** A row read off one of IGDB's popularity tables, in that table's own order. */
        static Definition ranked(String id, String label, boolean onHome, int popularityType) {
            return new Definition(id, label, onHome, popularityType, null);
        }

        /** One genre's best. Never on the home page: a home leads, it does not categorise. */
        static Definition genre(String id, String label, int genreId) {
            return new Definition(id, label, false, null, genreId);
        }
    }

    /**
     * Browse order, and the home page takes its four from it in place. What people are doing
     * now, then what is out and what is coming, then the genres.
     */
    private static final List<Definition> SHELVES = List.of(
            Definition.ranked(SHELF_POPULAR, "Popular now", true, POPULARITY_VISITS),
            Definition.ranked(SHELF_PLAYING, "Most played now", false, POPULARITY_PLAYING),
            Definition.ranked(SHELF_ANTICIPATED, "Most anticipated", false, POPULARITY_WISHLISTED_UPCOMING),
            Definition.query(SHELF_TOP_RATED, "Top rated", true),
            Definition.query(SHELF_RECENT, "Recently released", true),
            Definition.query(SHELF_COMING_SOON, "Coming soon", true),
            Definition.genre("rpg", "Role-playing", GENRE_RPG),
            Definition.genre("shooter", "Shooters", GENRE_SHOOTER),
            Definition.genre("strategy", "Strategy", GENRE_STRATEGY),
            Definition.genre("indie", "Indie", GENRE_INDIE),
            Definition.ranked(SHELF_MOST_PLAYED, "Most played of all time", false, POPULARITY_PLAYED));

    private IgdbShelves() {}

    static List<BrowseShelf> shelves() {
        return SHELVES.stream()
                .map(definition -> new BrowseShelf(definition.id(), definition.label(), definition.onHome(), true))
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
