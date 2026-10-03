package dev.nexus.modules.anime;

import static dev.nexus.core.adapter.Payloads.putIfPresent;
import static dev.nexus.core.adapter.Payloads.string;

import dev.nexus.core.adapter.BrowseResults;
import dev.nexus.core.adapter.BrowseShelf;
import dev.nexus.core.adapter.CharacterPortrait;
import dev.nexus.core.adapter.DiscoverFilters;
import dev.nexus.core.adapter.FilterField;
import dev.nexus.core.adapter.FetchProgress;
import dev.nexus.core.adapter.HeldOptions;
import dev.nexus.core.adapter.ItemSearchResult;
import dev.nexus.core.adapter.StudioBrowse;
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
public class AniListMetadataAdapter implements MetadataAdapter, StudioBrowse {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AniListMetadataAdapter.class);

    private final AniListClient client;

    /**
     * The genre list, fetched once and kept. It is the same answer for everyone and changes
     * about never, so asking AniList for it on every visit to a browse page would spend a
     * request to be told the same eighteen words.
     */
    private final HeldOptions<String> genres = new HeldOptions<>();
    private final HeldOptions<String> tags = new HeldOptions<>();

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

    /** Manga carry no chapter schedule, so publication starting is the one thing to tell. */
    @Override
    public boolean announcesReleaseStart(MediaType mediaType) {
        return mediaType == MediaType.MANGA;
    }

    @Override
    public List<ItemSearchResult> search(MediaType mediaType, String query, int limit, boolean includeAdult) {
        return client.searchMedia(mediaType, query, limit, includeAdult).stream()
                .map(media -> new ItemSearchResult(
                        mediaTypeOf(media),
                        Source.ANILIST,
                        string(media.get("id")),
                        title(media),
                        coverUrl(media),
                        releaseDate(media),
                        isAdult(media)))
                .toList();
    }

    @Override
    public Optional<TrackableItemData> fetchById(String externalId) {
        if (!isAniListId(externalId)) {
            return Optional.empty();
        }
        return client.findMediaById(externalId).stream().findFirst().map(this::toItemData);
    }

    /**
     * Whether AniList could have issued this id at all. Ids arrive straight from a URL and
     * AniList's are ints, which the client parses before asking — so a word, or a number past
     * an int, is something AniList does not have rather than a 500 from the catch-all.
     */
    private static boolean isAniListId(String id) {
        try {
            Integer.parseInt(id);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
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

    @Override
    public List<FilterField> discoverFilters(MediaType mediaType) {
        return AniListFilters.forMediaType(mediaType, genres(), tags(), LocalDate.now());
    }

    @Override
    public BrowseResults discover(
            MediaType mediaType, DiscoverFilters filters, int page, int size, boolean includeAdult) {
        List<String> narrowed = filters.all("genres");

        AniListClient.MediaPage found = client.discoverMedia(
                mediaType,
                filters.one("q"),
                chosen(narrowed, AniListFilters.GENRE),
                chosen(narrowed, AniListFilters.TAG),
                filters.number("year"),
                filters.one("season"),
                filters.one("format"),
                filters.one("status"),
                page,
                size,
                includeAdult);

        return new BrowseResults(
                found.media().stream().map(this::toSearchResult).toList(), found.hasNextPage());
    }

    /**
     * What a studio made, newest first.
     *
     * <p>Their whole catalogue rather than the anime half of it: AniList files a studio's
     * manga adaptations and its films together, and a page called "everything by Pierrot"
     * that quietly dropped some of it would be lying by omission.
     */
    @Override
    public Works worksOf(String studioId, int page, int size) {
        if (!isAniListId(studioId)) {
            return Works.none();
        }
        AniListClient.StudioPage found = client.fetchStudioWorks(studioId, page, size);

        return new Works(
                found.name(),
                found.media().stream().map(this::toSearchResult).toList(),
                found.hasNextPage());
    }

    /**
     * The chosen values belonging to one side of the box, with their mark taken off.
     *
     * <p>An unmarked value is read as a genre: that is what the field held before tags joined
     * it, and a link or a bookmark from then still means what it said.
     */
    private static List<String> chosen(List<String> values, String prefix) {
        boolean genres = AniListFilters.GENRE.equals(prefix);

        return values.stream()
                .filter(value -> value.startsWith(prefix)
                        || (genres && !value.startsWith(AniListFilters.TAG)))
                .map(value -> value.startsWith(prefix) ? value.substring(prefix.length()) : value)
                .toList();
    }

    /**
     * A missing genre list must not cost the reader the whole filter bar, so a failure falls
     * back to the known list rather than propagating, until it is worth asking again.
     */
    private List<String> genres() {
        return genres.get(client::genres, AniListFilters.FALLBACK_GENRES, e ->
                log.warn("Could not fetch the AniList genre list, using the known one: {}", e.toString()));
    }

    /**
     * The tag list, held for the life of the process like the genres.
     *
     * <p>No fallback list to fall back to — there are hundreds of them and they move — so a
     * failure leaves the field empty and the rest of the bar standing. A reader arriving from
     * a tag on a detail page still gets their answer, since the filter is sent by name.
     */
    private List<String> tags() {
        return tags.get(client::tags, List.of(), e ->
                log.warn("Could not fetch the AniList tag list, leaving the filter empty: {}", e.toString()));
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
                isAdult(media),
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
        if (!isAniListId(externalId)) {
            return Optional.empty();
        }
        Map<String, Object> detail = client.findMediaDetail(externalId);
        return detail.isEmpty() ? Optional.empty() : Optional.of(detail);
    }

    /** Relations and recommendations are where AniList's detail points at other titles, so they are what goes. */
    @Override
    public Map<String, Object> withoutAdult(Map<String, Object> detail) {
        Map<String, Object> copy = new HashMap<>(detail);
        boolean changed = dropAdult(copy, "relations", "edges", "node");
        changed |= dropAdult(copy, "recommendations", "nodes", "mediaRecommendation");
        return changed ? copy : detail;
    }

    /** Drops the rows of one connection whose title is adult; true when it dropped any. */
    private boolean dropAdult(Map<String, Object> detail, String connection, String rowsKey, String mediaKey) {
        if (!(detail.get(connection) instanceof Map<?, ?> rows) || !(rows.get(rowsKey) instanceof List<?> all)) {
            return false;
        }
        List<?> kept = all.stream()
                .filter(row -> !(row instanceof Map<?, ?> entry
                        && entry.get(mediaKey) instanceof Map<?, ?> media
                        && Boolean.TRUE.equals(media.get("isAdult"))))
                .toList();
        if (kept.size() == all.size()) {
            return false;
        }
        detail.put(connection, Map.of(rowsKey, kept));
        return true;
    }

    /** AniList names its own banner, and leaves it null for a title that has none. */
    @Override
    public Optional<String> bannerFrom(Map<String, Object> detail) {
        return Optional.ofNullable(string(detail.get("bannerImage"))).filter(url -> !url.isBlank());
    }

    /**
     * A character out of the detail's own edge list, by AniList's id for it.
     *
     * <p>Only characters with a portrait are offered. One without an image is still a real
     * character, but it cannot stand as a picture, and answering with it would hand back a
     * choice that draws as nothing.
     */
    @Override
    public Optional<CharacterPortrait> characterFrom(Map<String, Object> detail, String characterId) {
        if (characterId == null
                || !(detail.get("characters") instanceof Map<?, ?> characters)
                || !(characters.get("edges") instanceof List<?> edges)) {
            return Optional.empty();
        }
        return edges.stream()
                .filter(Map.class::isInstance)
                .map(edge -> ((Map<?, ?>) edge).get("node"))
                .filter(Map.class::isInstance)
                .map(node -> (Map<?, ?>) node)
                .filter(node -> characterId.equals(string(node.get("id"))))
                .findFirst()
                .flatMap(this::portrait);
    }

    private Optional<CharacterPortrait> portrait(Map<?, ?> node) {
        String name = node.get("name") instanceof Map<?, ?> names ? string(names.get("full")) : null;
        String image = node.get("image") instanceof Map<?, ?> images ? string(images.get("medium")) : null;
        if (name == null || name.isBlank() || image == null || image.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new CharacterPortrait(string(node.get("id")), name, image));
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
                isAdult(media),
                metadata(media));
    }

    /** The flag AniList's own 18+ setting reads, so what it hides is what a reader expects. */
    private boolean isAdult(Map<String, Object> media) {
        return Boolean.TRUE.equals(media.get("isAdult"));
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

    /**
     * The extra-large cover where the query asked for it: "large" is some 230 pixels wide,
     * under the 240 a title's page draws it at. Related titles ask for large alone, since
     * they only ever show as tiles.
     */
    private String coverUrl(Map<String, Object> media) {
        if (!(media.get("coverImage") instanceof Map<?, ?> cover)) {
            return null;
        }
        String sharpest = string(cover.get("extraLarge"));
        return sharpest != null ? sharpest : string(cover.get("large"));
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

        putIfPresent(metadata, "nextEpisode", nextEpisode(media));
        return metadata;
    }

    /**
     * When the next episode lands, on the item rather than only in its detail.
     *
     * <p>A page that lists what someone is part-way through wants the countdown beside every
     * title, and fetching each one's detail to find it would be a request per row. It rides
     * the list fields instead, which the refresh already re-reads every day for anything still
     * airing.
     *
     * <p>Stored as the absolute time AniList gives, never as a countdown: a duration cached
     * for a day is wrong by a day, while a timestamp stays true and the counting happens on
     * screen.
     */
    private Map<String, Object> nextEpisode(Map<String, Object> media) {
        if (!(media.get("nextAiringEpisode") instanceof Map<?, ?> next)) {
            return null;
        }

        Object episode = next.get("episode");
        Object airingAt = next.get("airingAt");
        if (episode == null || airingAt == null) {
            return null;
        }
        // The time is kept as a long: number() narrows to an int, which an epoch second
        // outgrows in 2038, and a countdown is exactly the field that would notice.
        return Map.of("episode", number(episode), "airingAt", epochSeconds(airingAt));
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

    private Integer number(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private Long epochSeconds(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
