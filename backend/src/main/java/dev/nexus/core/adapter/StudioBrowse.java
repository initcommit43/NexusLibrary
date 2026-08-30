package dev.nexus.core.adapter;

import dev.nexus.core.domain.Source;
import java.util.List;

/**
 * A source that can answer "what else did they make".
 *
 * <p>Optional, like {@code browseShelves}: a studio is a credit some catalogues keep and
 * others do not, and a module gains this page by implementing it rather than by anything in
 * core changing. AniList keeps studios and producers; TMDB keeps companies and networks in the
 * same shape, which is why this is worth a contract rather than an anime-only endpoint.
 */
public interface StudioBrowse {

    Source source();

    /**
     * One page of what a studio made, newest first.
     *
     * @param studioId the source's own id for the studio, as a title's page linked to
     */
    Works worksOf(String studioId, int page, int size);

    /**
     * @param name what the studio is called, for the page to head itself with
     * @param items the works, newest first
     * @param hasMore whether there is another page behind this one
     */
    record Works(String name, List<ItemSearchResult> items, boolean hasMore) {

        public static Works none() {
            return new Works(null, List.of(), false);
        }
    }
}
