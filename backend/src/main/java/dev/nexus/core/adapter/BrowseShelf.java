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
 */
public record BrowseShelf(String id, String label) {}
