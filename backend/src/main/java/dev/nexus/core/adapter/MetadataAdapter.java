package dev.nexus.core.adapter;

import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * Implemented per module, consumed by core. Adapters are dumb producers: they translate
 * one external API and never touch the database, so resolution and caching stay in one
 * place regardless of how many modules exist.
 */
public interface MetadataAdapter {

    /**
     * The media types this adapter serves. Usually one, but a source can be canonical for
     * several: AniList covers anime and manga, TMDB covers films and shows.
     */
    Set<MediaType> mediaTypes();

    Source source();

    List<ItemSearchResult> search(MediaType mediaType, String query, int limit);

    Optional<TrackableItemData> fetchById(String externalId);

    /**
     * The rows this source can fill a browse page with, in the order they should appear.
     *
     * <p>Empty by default, which reads as "this module has no browse page yet" rather than as
     * an error — a source with no notion of popularity should not have to pretend to one.
     */
    default List<BrowseShelf> browseShelves(MediaType mediaType) {
        return List.of();
    }

    /**
     * One page of a shelf. Called with an id this adapter itself returned from
     * {@link #browseShelves}, so an unknown id is a bug rather than user input.
     *
     * <p>Results are the same for everyone, which is what lets core cache the first page once
     * and serve every reader from that copy instead of spending a request per visitor.
     *
     * @param page one-based, so the shelf row on a browse page is simply page one
     */
    default BrowseResults browse(MediaType mediaType, String shelfId, int page, int size) {
        return BrowseResults.empty();
    }

    /**
     * The filter bar this media type can offer, in the order the controls should appear.
     *
     * <p>Empty by default, which reads as "this module cannot be narrowed yet" rather than as
     * an error — the browse page simply shows its shelves and no bar.
     */
    default List<FilterField> discoverFilters(MediaType mediaType) {
        return List.of();
    }

    /**
     * One page of titles matching a filter bar's values.
     *
     * <p>Separate from {@link #browse} because the answers are not the same for everyone: a
     * shelf is one list core can cache once and hand to every reader, while every combination
     * of filters is a different question. Core spends a request per combination and rate
     * limits accordingly.
     *
     * @param page one-based
     */
    default BrowseResults discover(MediaType mediaType, DiscoverFilters filters, int page, int size) {
        return BrowseResults.empty();
    }

    /**
     * Everything a source knows about one item beyond the fields core models — relations,
     * characters, tags, rankings. Shape is the source's own; only that module's UI reads it.
     *
     * <p>Separate from {@link #fetchById} because it is far heavier and wanted only when
     * someone opens a title, not for every row of an import.
     */
    default Optional<Map<String, Object>> fetchDetail(String externalId) {
        return Optional.empty();
    }

    /**
     * The wide key art in a detail this adapter itself wrote, if it has any.
     *
     * <p>Where that lives is the source's own business — AniList names a banner outright, a
     * film has its widest still, a game its first screenshot — and so the reading belongs
     * here rather than in whatever asks. A source with no wide art has none, which is an
     * answer rather than a failure.
     */
    default Optional<String> bannerFrom(Map<String, Object> detail) {
        return Optional.empty();
    }

    /**
     * Fetches many items at once. Importing a library needs hundreds of items, and one
     * request each would spend minutes inside the source's rate limit.
     *
     * <p>The default is a correct-but-slow loop so a module can ignore this until it has a
     * reason to care; sources with a bulk endpoint should override it.
     */
    default List<TrackableItemData> fetchByIds(Collection<String> externalIds) {
        return externalIds.stream().map(this::fetchById).flatMap(Optional::stream).toList();
    }

    /**
     * The same fetch, reporting how far through it is.
     *
     * <p>The default reports once at the end, which is honest for a source that answers in
     * one call and no worse than silence for one that does not. A source batching hundreds
     * of ids should override this and report per batch, since that is the wait a reader is
     * actually sitting through.
     */
    default List<TrackableItemData> fetchByIds(Collection<String> externalIds, FetchProgress progress) {
        List<TrackableItemData> fetched = fetchByIds(externalIds);
        progress.report(externalIds.size(), externalIds.size());
        return fetched;
    }
}
