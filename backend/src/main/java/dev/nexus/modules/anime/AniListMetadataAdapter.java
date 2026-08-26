package dev.nexus.modules.anime;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.FetchProgress;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.MetadataAdapter;
import dev.nexus.core.adapter.TrackableItemData;
import dev.nexus.core.domain.ItemState;
import dev.nexus.core.domain.MediaType;
import dev.nexus.core.domain.Source;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AniList is canonical for both anime and manga, so one adapter serves both: the id space is
 * shared, and a media record says which it is.
 */
@org.springframework.stereotype.Component
public class AniListMetadataAdapter implements MetadataAdapter {

    private final AniListClient client;

    public AniListMetadataAdapter(AniListClient client) {
        this.client = client;
    }

    @Override
    public Set<MediaType> mediaTypes() {
        return Set.of(MediaType.ANIME, MediaType.MANGA);
    }

    @Override
    public Source source() {
        return Source.ANILIST;
    }

    @Override
    public List<ItemSearchResult> search(MediaType mediaType, String query, int limit) {
        return client.searchMedia(mediaType, query, limit).stream()
                .map(media -> new ItemSearchResult(
                        mediaTypeOf(media),
                        Source.ANILIST,
                        string(media.get("id")),
                        title(media),
                        coverUrl(media),
                        releaseDate(media)))
                .toList();
    }

    @Override
    public Optional<TrackableItemData> fetchById(String externalId) {
        return client.findMediaById(externalId).stream().findFirst().map(this::toItemData);
    }

    @Override
    public List<BrowseShelf> browseShelves(MediaType mediaType) {
        return AniListShelves.shelvesFor(mediaType);
    }

    @Override
    public BrowseResults browse(MediaType mediaType, String shelfId, int page, int size) {
        AniListShelves.Definition shelf = AniListShelves.find(mediaType, shelfId);
        if (shelf == null) {
            return BrowseResults.empty();
        }

        LocalDate today = LocalDate.now();
        AniListClient.MediaPage found = client.browseMedia(
                mediaType,
                shelf.sort(),
                shelf.season(today),
                shelf.seasonYear(today),
                shelf.status(),
                shelf.format(),
                page,
                size);

        return new BrowseResults(
                found.media().stream().map(this::toSearchResult).toList(), found.hasNextPage());
    }

    /**
     * A browse hit carries a little more than a search hit does: a ranked shelf shows a score
     * and a format beside the title, and re-fetching each title to find them out would cost a
     * request per row.
     */
    private ItemSearchResult toSearchResult(Map<String, Object> media) {
        Map<String, Object> facets = new HashMap<>();
        putIfPresent(facets, "format", string(media.get("format")));
        putIfPresent(facets, "status", string(media.get("status")));
        putIfPresent(facets, "episodes", number(media.get("episodes")));
        putIfPresent(facets, "chapters", number(media.get("chapters")));
        // Already the 0-100 scale used internally, so nothing to convert.
        putIfPresent(facets, "score", number(media.get("averageScore")));
        if (media.get("genres") instanceof List<?> genres && !genres.isEmpty()) {
            facets.put("genres", genres.stream().limit(4).map(String::valueOf).toList());
        }

        return new ItemSearchResult(
                mediaTypeOf(media),
                Source.ANILIST,
                string(media.get("id")),
                title(media),
                coverUrl(media),
                releaseDate(media),
                Map.copyOf(facets));
    }


    @Override
    public List<TrackableItemData> fetchByIds(Collection<String> externalIds) {
        return fetchByIds(externalIds, FetchProgress.IGNORED);
    }

    /**
     * Fifty ids a call, paced against AniList's rate limit — a first import of several
     * hundred titles is most of the wait, so it is counted a batch at a time.
     */
    @Override
    public List<TrackableItemData> fetchByIds(Collection<String> externalIds, FetchProgress progress) {
        List<TrackableItemData> items = new ArrayList<>();
        int fetched = 0;
        for (List<String> batch : AniListClient.partition(externalIds)) {
            client.findMediaByIds(batch).stream().map(this::toItemData).forEach(items::add);
            fetched += batch.size();
            progress.report(fetched, externalIds.size());
        }
        return items;
    }

    @Override
    public Optional<Map<String, Object>> fetchDetail(String externalId) {
        Map<String, Object> detail = client.findMediaDetail(externalId);
        return detail.isEmpty() ? Optional.empty() : Optional.of(detail);
    }

    /** The MAL import's hard ID join: one call resolves a MAL id onto its AniList canonical. */
    public Optional<TrackableItemData> fetchByMalId(MediaType mediaType, String malId) {
        return client.findMediaByMalId(mediaType, malId).stream().findFirst().map(this::toItemData);
    }

    TrackableItemData toItemData(Map<String, Object> media) {
        return new TrackableItemData(
                mediaTypeOf(media),
                Source.ANILIST,
                string(media.get("id")),
                title(media),
                coverUrl(media),
                releaseDate(media),
                itemState(media),
                metadata(media));
    }

    private MediaType mediaTypeOf(Map<String, Object> media) {
        return "MANGA".equals(string(media.get("type"))) ? MediaType.MANGA : MediaType.ANIME;
    }

    /**
     * English first, then romaji, then native. A romaji fallback is not a nicety: plenty of
     * titles have no English entry at all, and native script is unsearchable for most users.
     */
    private String title(Map<String, Object> media) {
        if (!(media.get("title") instanceof Map<?, ?> titles)) {
            return string(media.get("id"));
        }
        return List.of("english", "romaji", "native").stream()
                .map(key -> string(titles.get(key)))
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseGet(() -> string(media.get("id")));
    }

    private String coverUrl(Map<String, Object> media) {
        return media.get("coverImage") instanceof Map<?, ?> cover ? string(cover.get("large")) : null;
    }

    /**
     * AniList reports a start date a piece at a time, and an announced title often has only
     * the year. A partial date is dropped rather than guessed into January the first.
     */
    private LocalDate releaseDate(Map<String, Object> media) {
        if (!(media.get("startDate") instanceof Map<?, ?> date)) {
            return null;
        }
        Integer year = number(date.get("year"));
        Integer month = number(date.get("month"));
        Integer day = number(date.get("day"));
        if (year == null || month == null || day == null) {
            return null;
        }
        return LocalDate.of(year, month, day);
    }

    private ItemState itemState(Map<String, Object> media) {
        return switch (String.valueOf(string(media.get("status")))) {
            case "RELEASING", "HIATUS" -> ItemState.ONGOING;
            case "NOT_YET_RELEASED" -> ItemState.UPCOMING;
            // FINISHED and CANCELLED are both settled: neither gains episodes from here.
            default -> ItemState.RELEASED;
        };
    }

    private Map<String, Object> metadata(Map<String, Object> media) {
        Map<String, Object> metadata = new HashMap<>();
        putIfPresent(metadata, "format", string(media.get("format")));
        putIfPresent(metadata, "summary", string(media.get("description")));
        putIfPresent(metadata, "genres", media.get("genres"));
        putIfPresent(metadata, "studios", studios(media));
        putIfPresent(metadata, "episodes", number(media.get("episodes")));
        putIfPresent(metadata, "chapters", number(media.get("chapters")));
        putIfPresent(metadata, "volumes", number(media.get("volumes")));

        // Kept for the MAL resolver: it collapses a MAL entry onto this canonical item, and
        // the guards that reject a bad title match compare episode counts and MAL ids.
        putIfPresent(metadata, "malId", number(media.get("idMal")));

        // AniList's averageScore is already the 0-100 scale used internally.
        putIfPresent(metadata, "externalRating", number(media.get("averageScore")));
        return metadata;
    }

    private List<String> studios(Map<String, Object> media) {
        if (!(media.get("studios") instanceof Map<?, ?> studios) || !(studios.get("nodes") instanceof List<?> nodes)) {
            return List.of();
        }
        return nodes.stream()
                .filter(Map.class::isInstance)
                .map(node -> ((Map<?, ?>) node).get("name"))
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            target.put(key, value);
        }
    }

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}
